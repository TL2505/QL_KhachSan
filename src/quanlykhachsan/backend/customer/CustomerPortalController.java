package quanlykhachsan.backend.customer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import quanlykhachsan.backend.booking.PublicBookingService;
import quanlykhachsan.backend.room.OptimisticLockException;
import quanlykhachsan.backend.room.RentalType;
import quanlykhachsan.backend.room.StayWindow;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.PortalValidation;

/** Customer portal API. Customer identity is resolved from the authenticated session. */
public final class CustomerPortalController implements HttpHandler {
    @Override public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (!"CUSTOMER".equalsIgnoreCase(String.valueOf(exchange.getAttribute("role")))) {
            ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Customer access required"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/api/v1/customer-portal/profile")) {
            updateProfile(exchange);
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/api/v1/customer-portal/bookings")) {
            createBooking(exchange);
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/api/v1/customer-portal/reviews")) {
            createReview(exchange);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            ApiResponseUtil.write(exchange, 405, ApiResponseUtil.error("Method not allowed"));
            return;
        }
        Object rawUserId = exchange.getAttribute("userId");
        if (!(rawUserId instanceof Number)) { ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("Invalid session")); return; }
        try (Connection connection = DatabaseConnection.getConnection()) {
            Integer customerId = customerId(connection, ((Number) rawUserId).intValue());
            if (customerId == null) { ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Customer profile is not linked")); return; }
            if (path.endsWith("/overview")) ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(overview(connection, customerId)));
            else if (path.endsWith("/bookings")) ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(bookings(connection, customerId)));
            else if (path.endsWith("/invoices")) ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(invoices(connection, customerId)));
            else if (path.endsWith("/profile")) ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(profile(connection, ((Number) rawUserId).intValue())));
            else if (path.equals("/api/v1/customer-portal/vouchers")) ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(vouchers(connection)));
            else ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Not found"));
        } catch (Exception ex) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Unable to load customer portal data"));
        }
    }

    private static JsonArray vouchers(Connection c) throws java.sql.SQLException {
        JsonArray result = new JsonArray();
        String sql = "SELECT code,name,discount_type,discount_value,max_discount,minimum_spend FROM promotions "
            + "WHERE status='active' AND CURRENT_TIMESTAMP BETWEEN start_date AND end_date "
            + "AND code IS NOT NULL AND max_discount>=0 AND minimum_spend>=0 AND max_uses_per_user>=1 "
            + "AND discount_type IN ('percentage','fixed_amount') AND applicable_scope IN ('ROOM','ALL') "
            + "AND (condition_type IS NULL OR condition_type IN ('none','room_type','min_stay','vip_only')) ORDER BY id";
        try (PreparedStatement q = c.prepareStatement(sql); ResultSet rows = q.executeQuery()) {
            while (rows.next()) {
                String code = rows.getString("code");
                if (!code.matches("[A-Za-z0-9_-]{1,50}")) continue;
                JsonObject voucher = new JsonObject();
                voucher.addProperty("code", code);
                voucher.addProperty("name", rows.getString("name"));
                voucher.addProperty("discount_type", rows.getString("discount_type"));
                voucher.addProperty("discount_value", rows.getBigDecimal("discount_value"));
                voucher.addProperty("max_discount", rows.getBigDecimal("max_discount"));
                voucher.addProperty("minimum_spend", rows.getBigDecimal("minimum_spend"));
                result.add(voucher);
            }
        }
        return result;
    }

    private static JsonObject profile(Connection c, int userId) throws Exception {
        return CustomerProfileService.read(c, userId);
    }

    private static void updateProfile(HttpExchange exchange) throws IOException {
        Object userId = exchange.getAttribute("userId");
        if (!(userId instanceof Number user)) { ApiResponseUtil.write(exchange,401,ApiResponseUtil.error("Invalid session")); return; }
        try {
            var update = CustomerProfileService.validate(PortalValidation.body(exchange));
            try (var c = DatabaseConnection.getConnection()) {
                var result = CustomerProfileService.save(c,user.intValue(),update,GuestIdentityVaultDao.configured());
                ApiResponseUtil.write(exchange,200,ApiResponseUtil.successWithData(result));
            }
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            ApiResponseUtil.write(exchange,400,ApiResponseUtil.error("Thông tin hồ sơ không hợp lệ. Kiểm tra số điện thoại, email, CCCD và ngày sinh."));
        } catch (java.sql.SQLException ex) {
            boolean conflict = ex.getSQLState()!=null && ex.getSQLState().startsWith("23");
            ApiResponseUtil.write(exchange,conflict?409:503,ApiResponseUtil.error(conflict?"Email hoặc số giấy tờ đã được sử dụng.":"Chưa thể lưu hồ sơ. Vui lòng thử lại."));
        } catch (IllegalStateException ex) {
            ApiResponseUtil.write(exchange,503,ApiResponseUtil.error("Chưa thể lưu hồ sơ. Vui lòng liên hệ lễ tân."));
        }
    }

    private static void createBooking(HttpExchange exchange) throws IOException {
        Object rawUserId = exchange.getAttribute("userId");
        if (!(rawUserId instanceof Number)) {
            ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("Invalid session"));
            return;
        }
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        try {
            if (key == null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                    || UUID.fromString(key).version() != 4) {
                throw new IllegalArgumentException("A UUIDv4 Idempotency-Key is required");
            }
            var body = PortalValidation.body(exchange);
            var confirmed = body.get("personal_info_confirmed");
            if (confirmed == null || !confirmed.isJsonPrimitive() || !confirmed.getAsJsonPrimitive().isBoolean()
                    || !confirmed.getAsBoolean()) {
                throw new IllegalArgumentException("Personal information must be confirmed");
            }
            var quotedTotalValue = body.get("quoted_total");
            if (quotedTotalValue == null || !quotedTotalValue.isJsonPrimitive()
                    || !quotedTotalValue.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("A quoted total is required");
            }
            var quotedTotal = quotedTotalValue.getAsBigDecimal();
            if (quotedTotal.signum() <= 0 || quotedTotal.scale() > 2) {
                throw new IllegalArgumentException("Invalid quoted total");
            }
            int roomId = PortalValidation.integer(body, "room_id", 1);
            int roomVersion = PortalValidation.integer(body, "room_version", 0);
            int guestCount = PortalValidation.integer(body, "guest_count", 1);
            LocalDate checkIn=LocalDate.parse(PortalValidation.text(body,"check_in_date",10,true));
            LocalDate checkOut=LocalDate.parse(PortalValidation.text(body,"check_out_date",10,true));
            RentalType requestedType=RentalType.parse(PortalValidation.text(body,"rental_type",20,false));
            LocalTime checkInTime=parseStayTime(body,"check_in_time",LocalTime.of(14,0));
            LocalTime checkOutTime=parseStayTime(body,"check_out_time",LocalTime.NOON);
            StayWindow stay=StayWindow.resolve(requestedType,checkIn.atTime(checkInTime),checkOut.atTime(checkOutTime));
            Integer customerId;
            try (Connection connection = DatabaseConnection.getConnection()) {
                customerId = customerId(connection, ((Number) rawUserId).intValue());
            }
            if (customerId == null) {
                ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Customer profile is not linked"));
                return;
            }
            String voucherCode = PortalValidation.text(body, "voucher_code", 50, false);
            var request = new PublicBookingService.Request(
                null, null, null, roomId, roomVersion, stay.checkIn().toLocalDate(), stay.checkOut().toLocalDate(),
                voucherCode, guestCount, quotedTotal, stay.rentalType(), stay.checkIn().toLocalTime(), stay.checkOut().toLocalTime());
            var result = new PublicBookingService().reserve(
                key.toLowerCase(java.util.Locale.ROOT), request, customerId, "Web");
            ApiResponseUtil.write(exchange, result.status(), result.body());
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Invalid booking data or Idempotency-Key"));
        } catch (OptimisticLockException | IllegalStateException ex) {
            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Room or request data changed; please check availability again"));
        } catch (Exception ex) {
            System.err.println("Customer booking failed: " + ex.getClass().getSimpleName());
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Booking is unavailable; please retry with the same Idempotency-Key or contact the Front Desk"));
        }
    }

    private static LocalTime parseStayTime(JsonObject body,String name,LocalTime fallback) {
        String value=PortalValidation.text(body,name,5,false);
        if(value==null)return fallback;
        try{return LocalTime.parse(value,java.time.format.DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT));}
        catch(java.time.DateTimeException ex){throw new IllegalArgumentException("Invalid "+name);}
    }

    private static Integer customerId(Connection c, int userId) throws Exception {
        try (PreparedStatement q = c.prepareStatement("SELECT customer_id FROM users WHERE id=?")) {
            q.setInt(1, userId); try (ResultSet r = q.executeQuery()) { return r.next() ? (Integer) r.getObject(1) : null; }
        }
    }
    private static void createReview(HttpExchange exchange) throws IOException {
        Object rawUserId = exchange.getAttribute("userId");
        if (!(rawUserId instanceof Number)) {
            ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("Invalid session"));
            return;
        }

        try {
            var body = PortalValidation.body(exchange);
            int bookingId = PortalValidation.integer(body, "booking_id", 1);
            var ratingValue = body.get("rating");
            if (ratingValue == null || !ratingValue.isJsonPrimitive()
                    || !ratingValue.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("A rating from 1 to 5 is required");
            }
            if (ratingValue.getAsBigDecimal().stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Rating must be a whole number from 1 to 5");
            }
            int rating = ratingValue.getAsInt();
            if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be from 1 to 5");
            var commentValue = body.get("comment");
            if (commentValue != null && !commentValue.isJsonNull()
                    && (!commentValue.isJsonPrimitive() || !commentValue.getAsJsonPrimitive().isString())) {
                throw new IllegalArgumentException("Review comment must be text");
            }
            String comment = commentValue == null || commentValue.isJsonNull() ? "" : commentValue.getAsString().trim();
            if (comment.length() > 1000) throw new IllegalArgumentException("Review comment is too long");

            try (Connection connection = DatabaseConnection.getConnection()) {
                Integer customerId = customerId(connection, ((Number) rawUserId).intValue());
                if (customerId == null) {
                    ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Customer profile is not linked"));
                    return;
                }
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM customers WHERE id=? FOR UPDATE")) {
                        lock.setInt(1, customerId);
                        try (ResultSet result = lock.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Customer profile is not linked"));
                                return;
                            }
                        }
                    }

                    int roomId;
                    try (PreparedStatement booking = connection.prepareStatement(
                            "SELECT room_id FROM bookings WHERE id=? AND customer_id=? AND status='checked_out' FOR UPDATE")) {
                        booking.setInt(1, bookingId);
                        booking.setInt(2, customerId);
                        try (ResultSet result = booking.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Only your completed stays can be reviewed"));
                                return;
                            }
                            roomId = result.getInt(1);
                        }
                    }

                    try (PreparedStatement existing = connection.prepareStatement(
                            "SELECT id FROM reviews WHERE customer_id=? AND room_id=? LIMIT 1")) {
                        existing.setInt(1, customerId);
                        existing.setInt(2, roomId);
                        try (ResultSet result = existing.executeQuery()) {
                            if (result.next()) {
                                connection.rollback();
                                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("You have already reviewed this room"));
                                return;
                            }
                        }
                    }

                    int reviewId;
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO reviews (room_id,customer_id,rating,comment) VALUES (?,?,?,?)",
                            PreparedStatement.RETURN_GENERATED_KEYS)) {
                        insert.setInt(1, roomId);
                        insert.setInt(2, customerId);
                        insert.setInt(3, rating);
                        insert.setString(4, comment.isEmpty() ? null : comment);
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (!keys.next()) throw new IllegalStateException("Review ID was not generated");
                            reviewId = keys.getInt(1);
                        }
                    }
                    connection.commit();
                    JsonObject result = new JsonObject();
                    result.addProperty("id", reviewId);
                    result.addProperty("bookingId", bookingId);
                    result.addProperty("rating", rating);
                    ApiResponseUtil.write(exchange, 201, ApiResponseUtil.successWithData(result));
                } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                }
            }
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error(ex.getMessage()));
        } catch (Exception ex) {
            System.err.println("Customer review failed: " + ex.getClass().getSimpleName());
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Review could not be saved; please try again"));
        }
    }
    private static JsonObject overview(Connection c, int customerId) throws Exception {
        JsonObject out = new JsonObject();
        try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*), COALESCE(SUM(status='pending'),0), COALESCE(SUM(status IN ('confirmed','pending')),0) FROM bookings WHERE customer_id=?")) {
            q.setInt(1, customerId); try (ResultSet r=q.executeQuery()) { r.next(); out.addProperty("bookingCount",r.getInt(1)); out.addProperty("pendingConfirmation",r.getInt(2)); out.addProperty("pendingPayment",r.getInt(3)); }
        }
        try (PreparedStatement q=c.prepareStatement("SELECT loyalty_points,total_loyalty_points,loyalty_level FROM customers WHERE id=?")) {
            q.setInt(1,customerId); try(ResultSet r=q.executeQuery()){ if(r.next()){out.addProperty("loyaltyPoints",r.getInt(1));out.addProperty("totalLoyaltyPoints",r.getInt(2));out.addProperty("loyaltyLevel",r.getString(3));} }
        }
        try (PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM promotions WHERE status='active' AND (start_date IS NULL OR start_date<=CURDATE()) AND (end_date IS NULL OR end_date>=CURDATE())")) {
            try(ResultSet r=q.executeQuery()){r.next();out.addProperty("activePromotions",r.getInt(1));}
        } catch (Exception ignored) { out.addProperty("activePromotions", 0); }
        return out;
    }
    private static JsonArray bookings(Connection c,int customerId)throws Exception {
        JsonArray out=new JsonArray();
        String sql = "SELECT b.id,b.room_id,r.room_number,b.check_in_date,b.check_out_date,b.total_price,b.status,"
            + "(SELECT SUM(f.balance) FROM folios f WHERE f.booking_id=b.id),i.id "
            + ",EXISTS (SELECT 1 FROM reviews rv WHERE rv.customer_id=b.customer_id AND rv.room_id=b.room_id) "
            + "FROM bookings b JOIN rooms r ON r.id=b.room_id LEFT JOIN invoices i ON i.booking_id=b.id "
            + "WHERE b.customer_id=? ORDER BY b.id DESC";
        try(PreparedStatement q=c.prepareStatement(sql)){
            q.setInt(1,customerId);try(ResultSet r=q.executeQuery()){while(r.next()){
                JsonObject x=new JsonObject();int id=r.getInt(1);
                x.addProperty("id",id);x.addProperty("bookingCode","BKG-WEB-"+r.getTimestamp(4).toLocalDateTime().getYear()+"-"+id);
                x.addProperty("roomId",r.getInt(2));x.addProperty("roomNumber",r.getString(3));
                x.addProperty("checkInDate",r.getTimestamp(4).toString());x.addProperty("checkOutDate",r.getTimestamp(5).toString());
                x.addProperty("totalPrice",r.getBigDecimal(6));x.addProperty("status",r.getString(7));
                var payment=quanlykhachsan.backend.payment.PaymentStatus.forBooking(c,id);x.addProperty("paymentStatus",payment.get("payment_status").getAsString());x.add("payment",payment);
                int invoiceId=r.getInt(9);if(!r.wasNull())x.addProperty("invoiceId",invoiceId);
                boolean hasReview=r.getBoolean(10);x.addProperty("hasReview",hasReview);
                x.addProperty("canReview","checked_out".equals(r.getString(7))&&!hasReview);
                out.add(x);
            }}}
        return out;
    }
    private static JsonArray invoices(Connection c,int customerId)throws Exception {
        JsonArray out=new JsonArray();
        try(PreparedStatement q=c.prepareStatement("SELECT i.id,i.booking_id,i.final_total,i.issue_date,i.status,r.room_number FROM invoices i JOIN bookings b ON b.id=i.booking_id JOIN rooms r ON r.id=b.room_id WHERE b.customer_id=? ORDER BY i.issue_date DESC,i.id DESC")){
            q.setInt(1,customerId);try(ResultSet r=q.executeQuery()){while(r.next()){JsonObject x=new JsonObject();x.addProperty("id",r.getInt(1));x.addProperty("bookingId",r.getInt(2));x.addProperty("finalTotal",r.getBigDecimal(3));x.addProperty("issueDate",r.getTimestamp(4).toString());x.addProperty("status",r.getString(5));x.addProperty("roomNumber",r.getString(6));out.add(x);}}}
        return out;
    }
}
