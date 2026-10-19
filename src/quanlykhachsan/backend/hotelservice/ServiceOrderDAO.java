package quanlykhachsan.backend.hotelservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * Data Access Object for in-stay room service orders.
 * Enforces atomic financial ledger integrity by linking service orders
 * directly to room Folio debit transactions within a single ACID transaction.
 */
public class ServiceOrderDAO {
    @FunctionalInterface public interface ConnectionSource { Connection open() throws SQLException; }
    private final ConnectionSource connections;
    public ServiceOrderDAO(){this(DatabaseConnection::getConnection);}
    public ServiceOrderDAO(ConnectionSource connections){this.connections=java.util.Objects.requireNonNull(connections);}

    /**
     * Creates a service order and immediately posts an atomic debit charge to the guest's folio.
     * If the folio debit or balance update fails, the entire transaction is rolled back.
     *
     * @param order The service order containing booking_id, category, item_name, quantity, etc.
     * @return The persisted ServiceOrder with generated ID and folio transaction details.
     * @throws SQLException If a database error occurs or if business validation fails.
     */
    public ServiceOrder createOrderWithFolioDebit(ServiceOrder order) throws SQLException {
        return createOrderWithFolioDebit(order, UUID.randomUUID().toString(), 0);
    }

    public ServiceOrder createOrderWithFolioDebit(ServiceOrder order, String requestKey, int expectedCustomer) throws SQLException {
        validate(order);
        if(requestKey==null || !requestKey.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException("UUIDv4 Idempotency-Key required");
        String hash=quanlykhachsan.backend.ai.AiInteractionDao.hash("service-order|"+expectedCustomer+"|"+quanlykhachsan.backend.utils.JsonUtil.getGson().toJson(order));
        var locks=new quanlykhachsan.backend.booking.FolioDao();
        Connection c = null;
        try {
            c = connections.open();
            locks.acquireLock(c,requestKey);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            var replay=locks.findReplay(c,requestKey);
            if(replay!=null){
                if(!hash.equals(replay.requestHash()))throw new IllegalStateException("Idempotency-Key already used with different data");
                c.rollback();return quanlykhachsan.backend.utils.JsonUtil.getGson().fromJson(replay.responseBody(),ServiceOrder.class);
            }

            // 1. Verify booking existence, in-house status (checked_in) and lock row
            String bookingSql = "SELECT b.customer_id, b.room_id, b.status, r.room_number, b.check_out_date " +
                                "FROM bookings b JOIN rooms r ON r.id = b.room_id WHERE b.id = ? FOR UPDATE";
            try (PreparedStatement q = c.prepareStatement(bookingSql)) {
                q.setInt(1, order.getBookingId());
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) {
                        throw new IllegalArgumentException("ERR_BOOKING_NOT_FOUND: Không tìm thấy đặt phòng #" + order.getBookingId());
                    }
                    int bCustomerId = r.getInt(1);
                    if(expectedCustomer>0 && expectedCustomer!=bCustomerId)throw new SecurityException("Booking ownership changed");
                    int bRoomId = r.getInt(2);
                    String bStatus = r.getString(3);
                    String roomNumber = r.getString(4);

                    if (!"checked_in".equalsIgnoreCase(bStatus)) {
                        throw new IllegalStateException("ERR_NOT_CHECKED_IN: Đặt phòng không ở trạng thái lưu trú (Status: " + bStatus + ")");
                    }
                    order.setCustomerId(bCustomerId);
                    order.setRoomId(bRoomId);
                    order.setRoomNumber(roomNumber);
                    if(order.getScheduledTime()!=null&&!order.getScheduledTime().before(r.getTimestamp(5)))throw new IllegalArgumentException("Service must be scheduled before checkout");
                }
            }

            int appliedPromoId=price(c,order);

            // 4. Insert service_orders record
            String insertOrderSql = "INSERT INTO service_orders (booking_id, customer_id, room_id, category, service_id, " +
                                   "item_name, quantity, unit_price, subtotal, discount_amount, final_price, voucher_code, " +
                                   "special_instructions, scheduled_time, order_status) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')";
            long orderId;
            try (PreparedStatement oq = c.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                oq.setInt(1, order.getBookingId());
                oq.setInt(2, order.getCustomerId());
                oq.setInt(3, order.getRoomId());
                oq.setString(4, order.getCategory() != null ? order.getCategory().toUpperCase() : "FNB");
                if (order.getServiceId() != null && order.getServiceId() > 0) {
                    oq.setInt(5, order.getServiceId());
                } else {
                    oq.setNull(5, Types.INTEGER);
                }
                oq.setString(6, order.getItemName());
                oq.setInt(7, order.getQuantity());
                oq.setBigDecimal(8, order.getUnitPrice());
                oq.setBigDecimal(9, order.getSubtotal());
                oq.setBigDecimal(10, order.getDiscountAmount());
                oq.setBigDecimal(11, order.getFinalPrice());
                oq.setString(12, order.getVoucherCode());
                oq.setString(13, order.getSpecialInstructions());
                oq.setTimestamp(14, order.getScheduledTime());
                oq.executeUpdate();
                try (ResultSet rs = oq.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Không thể lấy ID đơn dịch vụ được tạo");
                    }
                    orderId = rs.getLong(1);
                    order.setId(orderId);
                }
            }

            // 5. Update promotions.used_count upon successful voucher usage
            if (appliedPromoId > 0) {
                String incSql = "UPDATE promotions SET used_count = used_count + 1 WHERE id = ?";
                try (PreparedStatement incQ = c.prepareStatement(incSql)) {
                    incQ.setInt(1, appliedPromoId);
                    incQ.executeUpdate();
                }
            }

            // 6. Folio Integration: Locate open folio for booking
            long folioId = 0;
            String findFolioSql = "SELECT id FROM folios WHERE booking_id = ? AND status = 'open' " +
                                  "ORDER BY (folio_type = 'guest') DESC, id LIMIT 1 FOR UPDATE";
            try (PreparedStatement fq = c.prepareStatement(findFolioSql)) {
                fq.setInt(1, order.getBookingId());
                try (ResultSet fr = fq.executeQuery()) {
                    if (fr.next()) {
                        folioId = fr.getLong(1);
                    }
                }
            }

            if (folioId == 0) {
                // Create guest folio if none currently open with unique code to satisfy uq_folio_code
                String uniqueFolioCode = "FOL-" + order.getBookingId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
                String createFolioSql = "INSERT INTO folios (booking_id, folio_type, balance, status, code) " +
                                        "VALUES (?, 'guest', 0.00, 'open', ?)";
                try (PreparedStatement cfq = c.prepareStatement(createFolioSql, Statement.RETURN_GENERATED_KEYS)) {
                    cfq.setInt(1, order.getBookingId());
                    cfq.setString(2, uniqueFolioCode);
                    cfq.executeUpdate();
                    try (ResultSet cfr = cfq.getGeneratedKeys()) {
                        if (!cfr.next()) {
                            throw new SQLException("Không thể khởi tạo Folio khách phát sinh");
                        }
                        folioId = cfr.getLong(1);
                    }
                }
            }
            order.setFolioId(folioId);

            // 7. Insert debit transaction into folio_transactions
            long transactionId = 0;
            String desc = "[Room Service] " + order.getItemName() + " (x" + order.getQuantity() + ")";
            String ref = "SERVICE-ORDER-" + orderId;
            String insertTransSql = "INSERT INTO folio_transactions (folio_id, transaction_type, charge_type, description, amount, business_date, reference_id) " +
                                    "VALUES (?, 'debit', 'service_charge', ?, ?, " +
                                    "COALESCE((SELECT business_date FROM hotel_business_state WHERE id=1), CURRENT_DATE()), ?)";
            try (PreparedStatement tq = c.prepareStatement(insertTransSql, Statement.RETURN_GENERATED_KEYS)) {
                tq.setLong(1, folioId);
                tq.setString(2, desc);
                tq.setBigDecimal(3, order.getFinalPrice());
                tq.setString(4, ref);
                int affected = tq.executeUpdate();
                if (affected != 1) {
                    throw new SQLException("Lỗi khi chèn giao dịch ghi nợ Folio");
                }
                try (ResultSet tr = tq.getGeneratedKeys()) {
                    if (tr.next()) {
                        transactionId = tr.getLong(1);
                        order.setFolioTransactionId(transactionId);
                    }
                    if(transactionId<=0)throw new SQLException("Folio transaction ID missing");
                }
            }

            // 8. Update folio balance
            String updateFolioSql = "UPDATE folios SET balance = balance + ?, status = 'open' WHERE id = ?";
            try (PreparedStatement ufq = c.prepareStatement(updateFolioSql)) {
                ufq.setBigDecimal(1, order.getFinalPrice());
                ufq.setLong(2, folioId);
                if (ufq.executeUpdate() != 1) {
                    throw new SQLException("Không thể cập nhật số dư Folio");
                }
            }

            // 9. Associate folio_transaction_id in service_orders
            if (transactionId > 0) {
                try (PreparedStatement uoq = c.prepareStatement("UPDATE service_orders SET folio_transaction_id = ? WHERE id = ?")) {
                    uoq.setLong(1, transactionId);
                    uoq.setLong(2, orderId);
                    uoq.executeUpdate();
                }
            }

            try(var q=c.prepareStatement("SELECT created_at FROM service_orders WHERE id=?")){q.setLong(1,orderId);try(var r=q.executeQuery()){if(!r.next())throw new SQLException("Order disappeared");order.setCreatedAt(r.getTimestamp(1));}}
            locks.reserveIdempotency(c,requestKey,hash);
            locks.saveReplay(c,requestKey,quanlykhachsan.backend.utils.JsonUtil.getGson().toJson(order));
            c.commit();
            return order;
        } catch (Exception ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // Suppress secondary rollback exception to preserve root cause
                }
            }
            throw ex;
        } finally {
            if (c != null) {
                locks.releaseLock(c,requestKey);
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {}
                try {
                    c.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    private int price(Connection c,ServiceOrder order)throws SQLException {
            // 2. Validate quantities and pricing - FORCE SERVER-SIDE PRICING FOR CATALOG SERVICES
            if (order.getServiceId() != null && order.getServiceId() > 0) {
                try (PreparedStatement sq = c.prepareStatement("SELECT price, name, category FROM services WHERE id = ? AND status='active' FOR UPDATE")) {
                    sq.setInt(1, order.getServiceId());
                    try (ResultSet sr = sq.executeQuery()) {
                        if (!sr.next()) {
                            throw new IllegalArgumentException("ERR_SERVICE_NOT_FOUND: Dịch vụ #" + order.getServiceId() + " không tồn tại");
                        }
                        // ALWAYS override with database price to prevent client-side price tampering
                        order.setUnitPrice(sr.getBigDecimal(1));
                        if(!order.getCategory().equals(sr.getString(3)))throw new IllegalArgumentException("Service category does not match catalog");
                        order.setItemName(sr.getString(2));
                    }
                }
            }

            else order.setUnitPrice(BigDecimal.ZERO); // Explicit complimentary housekeeping request.
            if(order.getUnitPrice()==null||order.getUnitPrice().signum()<0)throw new IllegalArgumentException("Invalid catalog price");

            BigDecimal subtotal = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            if(subtotal.compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("Order total exceeds ledger limit");
            order.setSubtotal(subtotal);

            // 3. Voucher evaluation & scope check & usage limits
            BigDecimal discount = BigDecimal.ZERO;
            int appliedPromoId = 0;
            if (order.getVoucherCode() != null && !order.getVoucherCode().trim().isEmpty()) {
                String promoSql = "SELECT id, discount_type, discount_value, max_discount, minimum_spend, applicable_scope, max_uses_per_user, condition_type, condition_value, special_occasion, valid_after_checkout_days " +
                                  "FROM promotions WHERE code = ? AND status = 'active' AND CURRENT_TIMESTAMP BETWEEN start_date AND end_date FOR UPDATE";
                try (PreparedStatement pq = c.prepareStatement(promoSql)) {
                    pq.setString(1, order.getVoucherCode().trim());
                    try (ResultSet pr = pq.executeQuery()) {
                        if (pr.next()) {
                            appliedPromoId = pr.getInt("id");
                            String type = pr.getString("discount_type");
                            BigDecimal val = pr.getBigDecimal("discount_value");
                            if(val==null||val.signum()<0||("percentage".equalsIgnoreCase(type)&&val.compareTo(new BigDecimal("100"))>0))throw new IllegalArgumentException("Invalid voucher value");
                            BigDecimal maxDisc = pr.getBigDecimal("max_discount");
                            BigDecimal minSpend = pr.getBigDecimal("minimum_spend");
                            String scope = pr.getString("applicable_scope");
                            int maxUsesPerUser = pr.getInt("max_uses_per_user");
                            boolean hasMaxUses = !pr.wasNull() && maxUsesPerUser > 0;
                            String occasion=pr.getString("special_occasion");
                            if(occasion!=null&&!occasion.isBlank()){
                                if(!java.util.Set.of("birthday","sinh_nhat").contains(occasion.toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Voucher occasion requires verified eligibility");
                                try(var birthday=c.prepareStatement("SELECT id FROM customers WHERE id=? AND DATE_FORMAT(date_of_birth,'%m-%d')=DATE_FORMAT(CURRENT_DATE,'%m-%d')")){
                                    birthday.setInt(1,order.getCustomerId());try(var eligible=birthday.executeQuery()){if(!eligible.next())throw new IllegalArgumentException("Birthday voucher eligibility is not verified");}
                                }
                            }
                            int retention=pr.getInt("valid_after_checkout_days");boolean retentionRequired=!pr.wasNull();
                            if(retentionRequired){
                                if(retention<0)throw new IllegalArgumentException("Invalid voucher retention window");
                                try(var retained=c.prepareStatement("SELECT id FROM bookings WHERE customer_id=? AND status='checked_out' AND check_out_actual BETWEEN DATE_SUB(CURRENT_TIMESTAMP,INTERVAL ? DAY) AND CURRENT_TIMESTAMP LIMIT 1")){
                                    retained.setInt(1,order.getCustomerId());retained.setInt(2,retention);try(var eligible=retained.executeQuery()){if(!eligible.next())throw new IllegalArgumentException("Voucher requires a verified recent checkout");}
                                }
                            }
                            String condition=pr.getString("condition_type"),conditionValue=pr.getString("condition_value");
                            if(condition!=null&&!condition.equals("none")){
                                try(var eligibility=c.prepareStatement("SELECT c.is_vip,r.room_type_id,DATEDIFF(b.check_out_date,b.check_in_date) FROM bookings b JOIN customers c ON c.id=b.customer_id JOIN rooms r ON r.id=b.room_id WHERE b.id=?")){
                                    eligibility.setInt(1,order.getBookingId());try(var er=eligibility.executeQuery()){
                                        if(!er.next())throw new IllegalArgumentException("Voucher eligibility unavailable");
                                        boolean eligible=switch(condition){
                                            case "vip_only" -> er.getBoolean(1);
                                            case "room_type" -> conditionValue!=null&&conditionValue.matches("[0-9]{1,9}")&&Integer.parseInt(conditionValue)==er.getInt(2);
                                            case "min_stay" -> conditionValue!=null&&conditionValue.matches("[1-9][0-9]{0,3}")&&er.getInt(3)>=Integer.parseInt(conditionValue);
                                            default -> false;
                                        };
                                        if(!eligible)throw new IllegalArgumentException("Voucher eligibility requirements are not met");
                                    }
                                }
                            }

                            // Verify applicable scope (ALL, FNB, SPA, ROOM)
                            if (scope != null && !scope.equalsIgnoreCase("ALL") &&
                                    !scope.equalsIgnoreCase(order.getCategory())) {
                                throw new IllegalArgumentException("ERR_VOUCHER_SCOPE: Mã ưu đãi " + order.getVoucherCode() +
                                        " chỉ áp dụng cho phân loại " + scope + ", không thể áp dụng cho " + order.getCategory());
                            }

                            if (minSpend != null && subtotal.compareTo(minSpend) < 0) {
                                throw new IllegalArgumentException("ERR_VOUCHER_MIN_SPEND: Đơn dịch vụ chưa đạt mức chi tiêu tối thiểu (" + minSpend + " VNĐ)");
                            }

                            // Verify max_uses_per_user constraint
                            if (hasMaxUses && order.getCustomerId() > 0) {
                                String countSql = "SELECT COUNT(*) FROM service_orders WHERE voucher_code = ? AND customer_id = ? AND order_status <> 'CANCELLED'";
                                try (PreparedStatement countQ = c.prepareStatement(countSql)) {
                                    countQ.setString(1, order.getVoucherCode().trim());
                                    countQ.setInt(2, order.getCustomerId());
                                    try (ResultSet countRs = countQ.executeQuery()) {
                                        if (countRs.next() && countRs.getInt(1) >= maxUsesPerUser) {
                                            throw new IllegalArgumentException("ERR_VOUCHER_LIMIT: Mã ưu đãi " + order.getVoucherCode().trim() +
                                                    " đã đạt giới hạn sử dụng (" + maxUsesPerUser + " lần) cho tài khoản này");
                                        }
                                    }
                                }
                            }

                            if ("percentage".equalsIgnoreCase(type)) {
                                discount = subtotal.multiply(val).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                                if (maxDisc != null && maxDisc.signum() > 0 && discount.compareTo(maxDisc) > 0) {
                                    discount = maxDisc;
                                }
                            } else {
                                discount = val.min(subtotal);
                            }
                        } else {
                            throw new IllegalArgumentException("ERR_VOUCHER_INVALID: Mã ưu đãi không tồn tại hoặc đã hết hạn");
                        }
                    }
                }
            }

            order.setDiscountAmount(discount);
            BigDecimal finalPrice = subtotal.subtract(discount);
            if (finalPrice.signum() < 0) {
                finalPrice = BigDecimal.ZERO;
            }
            order.setFinalPrice(finalPrice);
            order.setOrderStatus("PENDING");

        return appliedPromoId;
    }

    public ServiceOrder previewOrder(ServiceOrder order,int expectedCustomer)throws SQLException {
        validate(order);
        try(var c=connections.open()){
            c.setAutoCommit(false);
            try{
                try(var q=c.prepareStatement("SELECT customer_id,status FROM bookings WHERE id=? FOR UPDATE")){
                    q.setInt(1,order.getBookingId());try(var r=q.executeQuery()){
                        if(!r.next()||!"checked_in".equals(r.getString(2)))throw new IllegalStateException("In-house booking required");
                        if(expectedCustomer>0&&expectedCustomer!=r.getInt(1))throw new SecurityException("Booking access denied");order.setCustomerId(r.getInt(1));
                    }
                }
                price(c,order);c.rollback();return order;
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }
    }

    public static void validate(ServiceOrder order) {
        if(order==null||order.getBookingId()<1||order.getQuantity()<1||order.getQuantity()>100)throw new IllegalArgumentException("Invalid booking or quantity (1–100)");
        if(!java.util.Set.of("FNB","SPA","HOUSEKEEPING").contains(order.getCategory()))throw new IllegalArgumentException("Invalid service category");
        if((order.getServiceId()==null||order.getServiceId()<1)&&!"HOUSEKEEPING".equals(order.getCategory()))throw new IllegalArgumentException("An active catalog service is required");
        if(order.getItemName()!=null&&order.getItemName().length()>150)throw new IllegalArgumentException("Item name too long");
        if("HOUSEKEEPING".equals(order.getCategory())&&(order.getServiceId()==null||order.getServiceId()<1)&&(order.getItemName()==null||order.getItemName().isBlank()))throw new IllegalArgumentException("Housekeeping request required");
        if(order.getSpecialInstructions()!=null&&order.getSpecialInstructions().length()>2000)throw new IllegalArgumentException("Instructions too long");
        if(order.getVoucherCode()!=null&&order.getVoucherCode().length()>50)throw new IllegalArgumentException("Voucher too long");
        if(order.getScheduledTime()!=null&&order.getScheduledTime().toInstant().isBefore(java.time.Instant.now()))throw new IllegalArgumentException("Schedule must be in the future");
    }

    /**
     * Retrieves active service orders in PENDING or ACCEPTED status for receptionist monitoring.
     */
    public List<ServiceOrder> getActiveOrders() throws SQLException {
        List<ServiceOrder> list = new ArrayList<>();
        String sql = "SELECT so.id, so.booking_id, so.customer_id, so.room_id, r.room_number, c.full_name as customer_name, " +
                     "so.category, so.service_id, so.item_name, so.quantity, so.unit_price, so.subtotal, so.discount_amount, so.final_price, " +
                     "so.voucher_code, so.special_instructions, so.scheduled_time, so.order_status, so.created_at, so.folio_transaction_id, (SELECT ft.folio_id FROM folio_transactions ft WHERE ft.id=so.folio_transaction_id) AS folio_id " +
                     "FROM service_orders so " +
                     "JOIN rooms r ON r.id = so.room_id " +
                     "JOIN customers c ON c.id = so.customer_id " +
                     "WHERE so.order_status IN ('PENDING', 'ACCEPTED') " +
                     "ORDER BY so.created_at ASC";
        try (Connection c = connections.open();
             PreparedStatement q = c.prepareStatement(sql);
             ResultSet r = q.executeQuery()) {
            while (r.next()) {
                ServiceOrder o = mapResultSetToOrder(r);
                list.add(o);
            }
        }
        return list;
    }

    /**
     * Retrieves all service orders associated with a booking.
     */
    public List<ServiceOrder> getOrdersByBookingId(int bookingId) throws SQLException {
        List<ServiceOrder> list = new ArrayList<>();
        String sql = "SELECT so.id, so.booking_id, so.customer_id, so.room_id, r.room_number, c.full_name as customer_name, " +
                     "so.category, so.service_id, so.item_name, so.quantity, so.unit_price, so.subtotal, so.discount_amount, so.final_price, " +
                     "so.voucher_code, so.special_instructions, so.scheduled_time, so.order_status, so.created_at, so.folio_transaction_id, (SELECT ft.folio_id FROM folio_transactions ft WHERE ft.id=so.folio_transaction_id) AS folio_id " +
                     "FROM service_orders so " +
                     "JOIN rooms r ON r.id = so.room_id " +
                     "JOIN customers c ON c.id = so.customer_id " +
                     "WHERE so.booking_id = ? " +
                     "ORDER BY so.created_at DESC";
        try (Connection c = connections.open();
             PreparedStatement q = c.prepareStatement(sql)) {
            q.setInt(1, bookingId);
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    ServiceOrder o = mapResultSetToOrder(r);
                    list.add(o);
                }
            }
        }
        return list;
    }

    /**
     * Updates the status of an existing service order (e.g., PENDING -> ACCEPTED -> DELIVERED -> COMPLETED).
     */
    public boolean updateOrderStatus(long orderId, String newStatus) throws SQLException {
        if(orderId<1||newStatus==null||!java.util.Set.of("ACCEPTED","DELIVERED","COMPLETED","CANCELLED").contains(newStatus))throw new IllegalArgumentException("Invalid order status");
        try(var c=connections.open()){
            c.setAutoCommit(false);
            try{
                int booking;
                try(var q=c.prepareStatement("SELECT booking_id FROM service_orders WHERE id=?")){q.setLong(1,orderId);try(var r=q.executeQuery()){if(!r.next()){c.rollback();return false;}booking=r.getInt(1);}}
                try(var q=c.prepareStatement("SELECT status FROM bookings WHERE id=? FOR UPDATE")){q.setInt(1,booking);try(var r=q.executeQuery()){if(!r.next()||!"checked_in".equals(r.getString(1)))throw new IllegalStateException("Booking is not in house");}}
                String status,voucher;long folio;BigDecimal amount;
                try(var q=c.prepareStatement("SELECT so.order_status,t.folio_id,so.final_price,so.voucher_code FROM service_orders so JOIN folio_transactions t ON t.id=so.folio_transaction_id WHERE so.id=? FOR UPDATE")){
                    q.setLong(1,orderId);try(var r=q.executeQuery()){if(!r.next())throw new SQLException("Order folio reference missing");status=r.getString(1);folio=r.getLong(2);amount=r.getBigDecimal(3);voucher=r.getString(4);}
                }
                if(status.equals(newStatus)){c.rollback();return true;}
                boolean valid=(status.equals("PENDING")&&java.util.Set.of("ACCEPTED","CANCELLED").contains(newStatus))||(status.equals("ACCEPTED")&&java.util.Set.of("DELIVERED","CANCELLED").contains(newStatus))||(status.equals("DELIVERED")&&newStatus.equals("COMPLETED"));
                if(!valid)throw new IllegalStateException("Invalid service order transition");
                if(newStatus.equals("CANCELLED")){
                    try(var q=c.prepareStatement("UPDATE folios SET balance=balance-? WHERE id=? AND status='open' AND balance>=?")){
                        q.setBigDecimal(1,amount);q.setLong(2,folio);q.setBigDecimal(3,amount);if(q.executeUpdate()!=1)throw new IllegalStateException("Paid service requires an authorized refund review");
                    }
                    try(var q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'credit','adjustment','Cancelled service order',?,business_date,? FROM hotel_business_state WHERE id=1")){
                        q.setLong(1,folio);q.setBigDecimal(2,amount);q.setString(3,"CANCEL-SERVICE-ORDER-"+orderId);if(q.executeUpdate()!=1)throw new SQLException("Business date missing");
                    }
                    if(voucher!=null)try(var q=c.prepareStatement("UPDATE promotions SET used_count=used_count-1 WHERE code=? AND used_count>0")){q.setString(1,voucher);q.executeUpdate();}
                }
                try(var q=c.prepareStatement("UPDATE service_orders SET order_status=? WHERE id=? AND order_status=?")){q.setString(1,newStatus);q.setLong(2,orderId);q.setString(3,status);if(q.executeUpdate()!=1)throw new SQLException("Order changed concurrently");}
                c.commit();return true;
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }
    }

    private ServiceOrder mapResultSetToOrder(ResultSet r) throws SQLException {
        ServiceOrder o = new ServiceOrder();
        o.setId(r.getLong("id"));
        o.setBookingId(r.getInt("booking_id"));
        o.setCustomerId(r.getInt("customer_id"));
        o.setRoomId(r.getInt("room_id"));
        o.setRoomNumber(r.getString("room_number"));
        o.setCustomerName(r.getString("customer_name"));
        o.setCategory(r.getString("category"));
        int sid = r.getInt("service_id");
        if (!r.wasNull()) {
            o.setServiceId(sid);
        }
        o.setItemName(r.getString("item_name"));
        o.setQuantity(r.getInt("quantity"));
        o.setUnitPrice(r.getBigDecimal("unit_price"));
        o.setSubtotal(r.getBigDecimal("subtotal"));
        o.setDiscountAmount(r.getBigDecimal("discount_amount"));
        o.setFinalPrice(r.getBigDecimal("final_price"));
        o.setVoucherCode(r.getString("voucher_code"));
        o.setSpecialInstructions(r.getString("special_instructions"));
        o.setScheduledTime(r.getTimestamp("scheduled_time"));
        o.setOrderStatus(r.getString("order_status"));
        long ftId = r.getLong("folio_transaction_id");
        if (!r.wasNull()) {
            o.setFolioTransactionId(ftId);
        }
        o.setFolioId(r.getLong("folio_id"));
        o.setCreatedAt(r.getTimestamp("created_at"));
        return o;
    }
}
