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

    /**
     * Creates a service order and immediately posts an atomic debit charge to the guest's folio.
     * If the folio debit or balance update fails, the entire transaction is rolled back.
     *
     * @param order The service order containing booking_id, category, item_name, quantity, etc.
     * @return The persisted ServiceOrder with generated ID and folio transaction details.
     * @throws SQLException If a database error occurs or if business validation fails.
     */
    public ServiceOrder createOrderWithFolioDebit(ServiceOrder order) throws SQLException {
        Connection c = null;
        try {
            c = DatabaseConnection.getConnection();
            c.setAutoCommit(false);

            // 1. Verify booking existence, in-house status (checked_in) and lock row
            String bookingSql = "SELECT b.customer_id, b.room_id, b.status, r.room_number " +
                                "FROM bookings b JOIN rooms r ON r.id = b.room_id WHERE b.id = ? FOR UPDATE";
            try (PreparedStatement q = c.prepareStatement(bookingSql)) {
                q.setInt(1, order.getBookingId());
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) {
                        throw new IllegalArgumentException("ERR_BOOKING_NOT_FOUND: Không tìm thấy đặt phòng #" + order.getBookingId());
                    }
                    int bCustomerId = r.getInt(1);
                    int bRoomId = r.getInt(2);
                    String bStatus = r.getString(3);
                    String roomNumber = r.getString(4);

                    if (!"checked_in".equalsIgnoreCase(bStatus)) {
                        throw new IllegalStateException("ERR_NOT_CHECKED_IN: Đặt phòng không ở trạng thái lưu trú (Status: " + bStatus + ")");
                    }
                    order.setCustomerId(bCustomerId);
                    order.setRoomId(bRoomId);
                    order.setRoomNumber(roomNumber);
                }
            }

            // 2. Validate quantities and pricing - FORCE SERVER-SIDE PRICING FOR CATALOG SERVICES
            if (order.getServiceId() != null && order.getServiceId() > 0) {
                try (PreparedStatement sq = c.prepareStatement("SELECT price, name FROM services WHERE id = ?")) {
                    sq.setInt(1, order.getServiceId());
                    try (ResultSet sr = sq.executeQuery()) {
                        if (!sr.next()) {
                            throw new IllegalArgumentException("ERR_SERVICE_NOT_FOUND: Dịch vụ #" + order.getServiceId() + " không tồn tại");
                        }
                        // ALWAYS override with database price to prevent client-side price tampering
                        order.setUnitPrice(sr.getBigDecimal(1));
                        if (order.getItemName() == null || order.getItemName().isBlank()) {
                            order.setItemName(sr.getString(2));
                        }
                    }
                }
            }

            if (order.getUnitPrice() == null || order.getUnitPrice().signum() < 0) {
                order.setUnitPrice(BigDecimal.ZERO);
            }

            BigDecimal subtotal = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            order.setSubtotal(subtotal);

            // 3. Voucher evaluation & scope check & usage limits
            BigDecimal discount = BigDecimal.ZERO;
            int appliedPromoId = 0;
            if (order.getVoucherCode() != null && !order.getVoucherCode().trim().isEmpty()) {
                String promoSql = "SELECT id, discount_type, discount_value, max_discount, minimum_spend, applicable_scope, max_uses_per_user " +
                                  "FROM promotions WHERE code = ? AND status = 'active' AND CURRENT_TIMESTAMP BETWEEN start_date AND end_date FOR UPDATE";
                try (PreparedStatement pq = c.prepareStatement(promoSql)) {
                    pq.setString(1, order.getVoucherCode().trim());
                    try (ResultSet pr = pq.executeQuery()) {
                        if (pr.next()) {
                            appliedPromoId = pr.getInt("id");
                            String type = pr.getString("discount_type");
                            BigDecimal val = pr.getBigDecimal("discount_value");
                            BigDecimal maxDisc = pr.getBigDecimal("max_discount");
                            BigDecimal minSpend = pr.getBigDecimal("minimum_spend");
                            String scope = pr.getString("applicable_scope");
                            int maxUsesPerUser = pr.getInt("max_uses_per_user");
                            boolean hasMaxUses = !pr.wasNull() && maxUsesPerUser > 0;

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

            c.commit(); // MUST BE PLACED AT THE END OF THE TRY BLOCK!
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
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {}
                try {
                    c.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Retrieves active service orders in PENDING or ACCEPTED status for receptionist monitoring.
     */
    public List<ServiceOrder> getActiveOrders() throws SQLException {
        List<ServiceOrder> list = new ArrayList<>();
        String sql = "SELECT so.id, so.booking_id, so.customer_id, so.room_id, r.room_number, c.full_name as customer_name, " +
                     "so.category, so.service_id, so.item_name, so.quantity, so.unit_price, so.subtotal, so.discount_amount, so.final_price, " +
                     "so.voucher_code, so.special_instructions, so.scheduled_time, so.order_status, so.created_at, so.folio_transaction_id " +
                     "FROM service_orders so " +
                     "JOIN rooms r ON r.id = so.room_id " +
                     "JOIN customers c ON c.id = so.customer_id " +
                     "WHERE so.order_status IN ('PENDING', 'ACCEPTED') " +
                     "ORDER BY so.created_at ASC";
        try (Connection c = DatabaseConnection.getConnection();
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
                     "so.voucher_code, so.special_instructions, so.scheduled_time, so.order_status, so.created_at, so.folio_transaction_id " +
                     "FROM service_orders so " +
                     "JOIN rooms r ON r.id = so.room_id " +
                     "JOIN customers c ON c.id = so.customer_id " +
                     "WHERE so.booking_id = ? " +
                     "ORDER BY so.created_at DESC";
        try (Connection c = DatabaseConnection.getConnection();
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
        String sql = "UPDATE service_orders SET order_status = ? WHERE id = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, newStatus.toUpperCase());
            q.setLong(2, orderId);
            return q.executeUpdate() > 0;
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
        o.setCreatedAt(r.getTimestamp("created_at"));
        return o;
    }
}
