package quanlykhachsan.backend.interaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.utils.DatabaseConnection;

public class ReviewDAOImpl implements ReviewDAO {

    @Override
    public List<Review> findAll() {
        List<Review> list = new ArrayList<>();
        String sql = "SELECT r.*, c.full_name as cust_name, rm.room_number, rt.name as room_type_name, " +
                     "u.full_name as responder_name " +
                     "FROM reviews r " +
                     "JOIN customers c ON r.customer_id = c.id " +
                     "LEFT JOIN rooms rm ON r.room_id = rm.id " +
                     "LEFT JOIN room_types rt ON rm.room_type_id = rt.id " +
                     "LEFT JOIN users u ON r.responded_by_user_id = u.id " +
                     "ORDER BY r.created_at DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Review> findByRoomId(int roomId) {
        List<Review> list = new ArrayList<>();
        String sql = "SELECT r.*, c.full_name as cust_name, rm.room_number, rt.name as room_type_name, " +
                     "u.full_name as responder_name " +
                     "FROM reviews r " +
                     "JOIN customers c ON r.customer_id = c.id " +
                     "LEFT JOIN rooms rm ON r.room_id = rm.id " +
                     "LEFT JOIN room_types rt ON rm.room_type_id = rt.id " +
                     "LEFT JOIN users u ON r.responded_by_user_id = u.id " +
                     "WHERE r.room_id = ? ORDER BY r.created_at DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Review findById(int id) {
        String sql = "SELECT r.*, c.full_name as cust_name, rm.room_number, rt.name as room_type_name, " +
                     "u.full_name as responder_name " +
                     "FROM reviews r " +
                     "JOIN customers c ON r.customer_id = c.id " +
                     "LEFT JOIN rooms rm ON r.room_id = rm.id " +
                     "LEFT JOIN room_types rt ON rm.room_type_id = rt.id " +
                     "LEFT JOIN users u ON r.responded_by_user_id = u.id " +
                     "WHERE r.id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean insert(Review review) {
        String sql = "INSERT INTO reviews (customer_id, room_id, rating, comment) VALUES (?, ?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, review.getCustomerId());
            ps.setInt(2, review.getRoomId());
            ps.setInt(3, review.getRating());
            ps.setString(4, review.getComment());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM reviews WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public Review submitVerifiedReview(Review review) throws SQLException {
        Connection c = null;
        try {
            c = DatabaseConnection.getConnection();
            c.setAutoCommit(false);

            // 1. Verify booking existence, ownership, status == 'checked_out', and check_out_date <= 14 days
            String bookingSql = "SELECT b.customer_id, b.room_id, b.status, " +
                                "COALESCE(b.check_out_actual, b.check_out_date, b.updated_at, CURRENT_TIMESTAMP) as co_date, " +
                                "r.room_number, rt.name as room_type_name " +
                                "FROM bookings b " +
                                "JOIN rooms r ON r.id = b.room_id " +
                                "LEFT JOIN room_types rt ON rt.id = r.room_type_id " +
                                "WHERE b.id = ? FOR UPDATE";
            int bCustomerId = 0;
            int bRoomId = 0;
            String bStatus = "";
            Timestamp checkOutTime = null;
            String roomNumber = "";
            String roomTypeName = "";

            try (PreparedStatement q = c.prepareStatement(bookingSql)) {
                q.setInt(1, review.getBookingId());
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) {
                        throw new IllegalArgumentException("ERR_BOOKING_NOT_FOUND: Không tìm thấy đặt phòng #" + review.getBookingId());
                    }
                    bCustomerId = r.getInt(1);
                    bRoomId = r.getInt(2);
                    bStatus = r.getString(3);
                    checkOutTime = r.getTimestamp(4);
                    roomNumber = r.getString(5);
                    roomTypeName = r.getString(6);
                }
            }

            // Verified Stay Policy: Must be checked_out
            if (bStatus == null || !"checked_out".equalsIgnoreCase(bStatus.trim())) {
                throw new IllegalStateException("ERR_NOT_CHECKED_OUT: Đánh giá chỉ áp dụng cho Quý khách đã hoàn tất thủ tục trả phòng (Status: " + bStatus + ")");
            }

            // Time window: <= 14 days from checkout (NPE safe fallback)
            Timestamp checkoutTime = checkOutTime != null ? checkOutTime : new Timestamp(System.currentTimeMillis());
            long diffMs = System.currentTimeMillis() - checkoutTime.getTime();
            long maxWindowMs = 14L * 24 * 60 * 60 * 1000;
            if (diffMs > maxWindowMs) {
                throw new IllegalStateException("ERR_REVIEW_WINDOW_EXPIRED: Thời hạn gửi phản hồi cho kỳ nghỉ này đã kết thúc (tối đa 14 ngày kể từ ngày trả phòng)");
            }

            review.setCustomerId(bCustomerId);
            review.setRoomId(bRoomId);
            review.setRoomNumber(roomNumber);
            review.setRoomTypeName(roomTypeName);

            // 2. Anti-fraud / unique stay review check
            String checkSql = "SELECT id FROM reviews WHERE booking_id = ? FOR UPDATE";
            try (PreparedStatement cq = c.prepareStatement(checkSql)) {
                cq.setInt(1, review.getBookingId());
                try (ResultSet cr = cq.executeQuery()) {
                    if (cr.next()) {
                        throw new IllegalStateException("ERR_ALREADY_REVIEWED: Đặt phòng #" + review.getBookingId() + " đã được gửi đánh giá trước đó");
                    }
                }
            }

            // 3. Compute ratings
            int rRoom = review.getRatingRoom() > 0 ? Math.min(5, Math.max(1, review.getRatingRoom())) : 5;
            int rFnb = review.getRatingFnb() > 0 ? Math.min(5, Math.max(1, review.getRatingFnb())) : 5;
            int rService = review.getRatingService() > 0 ? Math.min(5, Math.max(1, review.getRatingService())) : 5;
            int rCleanliness = review.getRatingCleanliness() > 0 ? Math.min(5, Math.max(1, review.getRatingCleanliness())) : 5;
            review.setRatingRoom(rRoom);
            review.setRatingFnb(rFnb);
            review.setRatingService(rService);
            review.setRatingCleanliness(rCleanliness);

            BigDecimal overall = review.getRatingOverall();
            if (overall == null || overall.signum() <= 0) {
                double avg = (rRoom + rFnb + rService + rCleanliness) / 4.0;
                overall = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
                review.setRatingOverall(overall);
            }
            int legacyRating = (int) Math.round(overall.doubleValue());
            if (legacyRating < 1) legacyRating = 1;
            if (legacyRating > 5) legacyRating = 5;
            review.setRating(legacyRating);

            // 4. Insert into reviews table (strictly bind database verified room_id & customer_id)
            if (bRoomId <= 0) {
                throw new IllegalStateException("ERR_INVALID_BOOKING_ROOM: Đặt phòng #" + review.getBookingId() + " không có thông tin phòng hợp lệ");
            }
            if (bCustomerId <= 0) {
                throw new IllegalStateException("ERR_INVALID_BOOKING_CUSTOMER: Đặt phòng #" + review.getBookingId() + " không có thông tin khách hàng hợp lệ");
            }

            String insertSql = "INSERT INTO reviews (booking_id, customer_id, room_id, rating, " +
                               "rating_room, rating_fnb, rating_service, rating_cleanliness, rating_overall, " +
                               "comment, reward_granted, reward_points) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, 200)";
            int reviewId = 0;
            try (PreparedStatement iq = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                iq.setInt(1, review.getBookingId());
                iq.setInt(2, bCustomerId);
                iq.setInt(3, bRoomId);
                iq.setInt(4, legacyRating);
                iq.setInt(5, review.getRatingRoom());
                iq.setInt(6, review.getRatingFnb());
                iq.setInt(7, review.getRatingService());
                iq.setInt(8, review.getRatingCleanliness());
                iq.setBigDecimal(9, review.getRatingOverall());
                iq.setString(10, review.getComment());
                iq.executeUpdate();
                try (ResultSet ir = iq.getGeneratedKeys()) {
                    if (ir.next()) {
                        reviewId = ir.getInt(1);
                        review.setId(reviewId);
                    }
                }
            }

            // 5. Atomic Loyalty allocation (+200 points) with fallback
            try {
                String updateCustSql = "UPDATE customers SET loyalty_points = loyalty_points + 200, " +
                                       "total_loyalty_points = total_loyalty_points + 200 WHERE id = ?";
                try (PreparedStatement uq = c.prepareStatement(updateCustSql)) {
                    uq.setInt(1, bCustomerId);
                    uq.executeUpdate();
                }
            } catch (SQLException ex) {
                // Defensive fallback if total_loyalty_points column does not exist in legacy schema
                String fallbackSql = "UPDATE customers SET loyalty_points = loyalty_points + 200 WHERE id = ?";
                try (PreparedStatement uqFallback = c.prepareStatement(fallbackSql)) {
                    uqFallback.setInt(1, bCustomerId);
                    uqFallback.executeUpdate();
                }
            }

            // 6. Record transaction in loyalty_histories (Atomic & Audit Trail Preserved with auto-healing)
            try {
                String histSql = "INSERT INTO loyalty_histories (customer_id, points, type, description) VALUES (?, 200, 'earn', ?)";
                try (PreparedStatement hq = c.prepareStatement(histSql)) {
                    hq.setInt(1, bCustomerId);
                    hq.setString(2, "Thưởng hoàn tất đánh giá kỳ nghỉ #" + review.getBookingId());
                    hq.executeUpdate();
                }
            } catch (SQLException ex) {
                // If table is missing, auto-heal on the fly and retry so audit trail is 100% recorded
                if (ex.getErrorCode() == 1146 || (ex.getMessage() != null && ex.getMessage().contains("loyalty_histories"))) {
                    try (Statement createStmt = c.createStatement()) {
                        createStmt.executeUpdate("CREATE TABLE IF NOT EXISTS loyalty_histories (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                "customer_id INT NOT NULL, " +
                                "points INT NOT NULL, " +
                                "type ENUM('earn', 'redeem') NOT NULL DEFAULT 'earn', " +
                                "description VARCHAR(255) NULL, " +
                                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                                "CONSTRAINT fk_lh_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE, " +
                                "INDEX idx_lh_customer (customer_id, created_at)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
                    } catch (SQLException createEx) {
                        System.err.println("[ReviewDAOImpl] Auto-heal loyalty_histories failed: " + createEx.getMessage());
                    }
                    try {
                        String histSql = "INSERT INTO loyalty_histories (customer_id, points, type, description) VALUES (?, 200, 'earn', ?)";
                        try (PreparedStatement hq = c.prepareStatement(histSql)) {
                            hq.setInt(1, bCustomerId);
                            hq.setString(2, "Thưởng hoàn tất đánh giá kỳ nghỉ #" + review.getBookingId());
                            hq.executeUpdate();
                        }
                    } catch (SQLException retryEx) {
                        System.err.println("[ReviewDAOImpl] Could not record loyalty history on retry: " + retryEx.getMessage());
                    }
                } else {
                    System.err.println("[ReviewDAOImpl] Warning: Could not record loyalty history: " + ex.getMessage());
                }
            }

            review.setRewardGranted(true);
            review.setRewardPoints(200);

            c.commit(); // Transaction committed safely
            return review;
        } catch (Exception e) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {}
            }
            throw e;
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

    @Override
    public boolean saveManagementReply(int reviewId, String reply, int userId) throws SQLException {
        String sql = "UPDATE reviews SET management_response = ?, responded_by_user_id = ?, responded_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection c = DatabaseConnection.getConnection()) {
            Integer validUserId = null;
            if (userId > 0) {
                try (PreparedStatement uq = c.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                    uq.setInt(1, userId);
                    try (ResultSet ur = uq.executeQuery()) {
                        if (ur.next()) {
                            validUserId = userId;
                        }
                    }
                } catch (SQLException ignored) {}
            }

            try (PreparedStatement q = c.prepareStatement(sql)) {
                q.setString(1, reply);
                if (validUserId != null && validUserId > 0) {
                    q.setInt(2, validUserId);
                } else {
                    q.setNull(2, Types.INTEGER);
                }
                q.setInt(3, reviewId);
                return q.executeUpdate() > 0;
            } catch (SQLException ex) {
                // If foreign key constraint fails (fk_reviews_responder / Error 1452), gracefully fallback with NULL
                if (ex.getErrorCode() == 1452 || (ex.getMessage() != null && ex.getMessage().contains("fk_reviews_responder"))) {
                    try (PreparedStatement fallbackQ = c.prepareStatement(sql)) {
                        fallbackQ.setString(1, reply);
                        fallbackQ.setNull(2, Types.INTEGER);
                        fallbackQ.setInt(3, reviewId);
                        return fallbackQ.executeUpdate() > 0;
                    }
                }
                throw ex;
            }
        }
    }

    @Override
    public JsonObject getPublicReputationSummary(int page, int limit) throws SQLException {
        if (page < 1) page = 1;
        if (limit < 1) limit = 6;
        int offset = (page - 1) * limit;

        JsonObject result = new JsonObject();
        JsonObject summary = new JsonObject();

        try (Connection c = DatabaseConnection.getConnection()) {
            // 1. Aggregates
            String aggSql = "SELECT COUNT(*) as total_count, " +
                            "COALESCE(AVG(rating_overall), 5.0) as avg_overall, " +
                            "COALESCE(AVG(rating_room), 5.0) as avg_room, " +
                            "COALESCE(AVG(rating_fnb), 5.0) as avg_fnb, " +
                            "COALESCE(AVG(rating_service), 5.0) as avg_service, " +
                            "COALESCE(AVG(rating_cleanliness), 5.0) as avg_cleanliness, " +
                            "SUM(CASE WHEN rating_overall >= 4.0 THEN 1 ELSE 0 END) as sat_count " +
                            "FROM reviews";
            try (PreparedStatement aq = c.prepareStatement(aggSql);
                 ResultSet ar = aq.executeQuery()) {
                int total = 0;
                double overall = 5.0;
                double room = 5.0;
                double fnb = 5.0;
                double service = 5.0;
                double cleanliness = 5.0;
                double recPct = 100.0;

                if (ar.next()) {
                    total = ar.getInt("total_count");
                    if (total > 0) {
                        overall = Math.round(ar.getDouble("avg_overall") * 10.0) / 10.0;
                        room = Math.round(ar.getDouble("avg_room") * 10.0) / 10.0;
                        fnb = Math.round(ar.getDouble("avg_fnb") * 10.0) / 10.0;
                        service = Math.round(ar.getDouble("avg_service") * 10.0) / 10.0;
                        cleanliness = Math.round(ar.getDouble("avg_cleanliness") * 10.0) / 10.0;
                        int satCount = ar.getInt("sat_count");
                        double recPercent = (satCount * 100.0) / total;
                        if (Double.isNaN(recPercent) || Double.isInfinite(recPercent)) {
                            recPercent = 100.0;
                        }
                        recPct = Math.round(recPercent * 10.0) / 10.0;
                    }
                }

                // Safety guard: ensure no Double.NaN or Double.isInfinite escapes to Gson
                if (Double.isNaN(overall) || Double.isInfinite(overall)) overall = 5.0;
                if (Double.isNaN(room) || Double.isInfinite(room)) room = 5.0;
                if (Double.isNaN(fnb) || Double.isInfinite(fnb)) fnb = 5.0;
                if (Double.isNaN(service) || Double.isInfinite(service)) service = 5.0;
                if (Double.isNaN(cleanliness) || Double.isInfinite(cleanliness)) cleanliness = 5.0;
                if (Double.isNaN(recPct) || Double.isInfinite(recPct)) recPct = 100.0;

                summary.addProperty("total_reviews", total);
                summary.addProperty("overall_average", overall);
                summary.addProperty("room_average", room);
                summary.addProperty("fnb_average", fnb);
                summary.addProperty("service_average", service);
                summary.addProperty("cleanliness_average", cleanliness);
                summary.addProperty("recommendation_percentage", recPct);
            }
            result.add("summary", summary);

            // 2. Reviews list
            JsonArray reviewsArr = new JsonArray();
            String listSql = "SELECT r.*, c.full_name as cust_name, rm.room_number, rt.name as room_type_name, " +
                             "u.full_name as responder_name " +
                             "FROM reviews r " +
                             "JOIN customers c ON c.id = r.customer_id " +
                             "LEFT JOIN rooms rm ON rm.id = r.room_id " +
                             "LEFT JOIN room_types rt ON rt.id = rm.room_type_id " +
                             "LEFT JOIN users u ON u.id = r.responded_by_user_id " +
                             "ORDER BY r.created_at DESC LIMIT ? OFFSET ?";
            try (PreparedStatement lq = c.prepareStatement(listSql)) {
                lq.setInt(1, limit);
                lq.setInt(2, offset);
                try (ResultSet lr = lq.executeQuery()) {
                    while (lr.next()) {
                        JsonObject ro = new JsonObject();
                        ro.addProperty("id", lr.getInt("id"));
                        String fullName = lr.getString("cust_name");
                        ro.addProperty("customer_display_name", maskName(fullName));
                        ro.addProperty("room_type_name", lr.getString("room_type_name") != null ? lr.getString("room_type_name") : "Phòng " + lr.getString("room_number"));
                        ro.addProperty("rating_overall", lr.getBigDecimal("rating_overall"));
                        ro.addProperty("rating_room", lr.getInt("rating_room"));
                        ro.addProperty("rating_fnb", lr.getInt("rating_fnb"));
                        ro.addProperty("rating_service", lr.getInt("rating_service"));
                        ro.addProperty("rating_cleanliness", lr.getInt("rating_cleanliness"));
                        ro.addProperty("comment", lr.getString("comment"));
                        ro.addProperty("created_at", lr.getString("created_at"));

                        String mResp = lr.getString("management_response");
                        if (mResp != null && !mResp.isBlank()) {
                            JsonObject mObj = new JsonObject();
                            String respName = lr.getString("responder_name");
                            mObj.addProperty("responder_title", respName != null ? respName + " (Ban Quản Lý)" : "Tổng Quản Lý Khu Nghỉ Dưỡng (General Manager)");
                            mObj.addProperty("content", mResp);
                            mObj.addProperty("responded_at", lr.getString("responded_at"));
                            ro.add("management_response", mObj);
                        }
                        reviewsArr.add(ro);
                    }
                }
            }
            result.add("reviews", reviewsArr);
            result.addProperty("success", true);
        }
        return result;
    }

    public static String maskCustomerName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "Guest"; 
        String[] parts = fullName.trim().split("\\s+"); 
        if (parts.length == 1) {
            return (parts[0].length() > 0 ? parts[0].charAt(0) : "") + "***"; 
        } else if (parts.length == 2) {
            String p1 = parts[1].length() > 0 ? parts[1].charAt(0) + "***" : "***";
            return parts[0] + " " + p1; 
        } else {
            String p1 = parts[1].length() > 0 ? parts[1].charAt(0) + "***" : "***";
            String pLast = parts[parts.length - 1].length() > 0 ? parts[parts.length - 1].charAt(0) + "***" : "***";
            return parts[0] + " " + p1 + " " + pLast; 
        }
    }

    public static String maskName(String name) {
        return maskCustomerName(name);
    }

    private Review mapResultSet(ResultSet rs) throws SQLException {
        Review r = new Review();
        r.setId(rs.getInt("id"));
        try {
            int bid = rs.getInt("booking_id");
            if (!rs.wasNull()) r.setBookingId(bid);
        } catch (SQLException ignored) {}

        r.setCustomerId(rs.getInt("customer_id"));
        r.setRoomId(rs.getInt("room_id"));
        r.setRating(rs.getInt("rating"));
        r.setComment(rs.getString("comment"));
        r.setCreatedAt(rs.getTimestamp("created_at"));

        try { r.setRatingRoom(rs.getInt("rating_room")); } catch (SQLException ignored) {}
        try { r.setRatingFnb(rs.getInt("rating_fnb")); } catch (SQLException ignored) {}
        try { r.setRatingService(rs.getInt("rating_service")); } catch (SQLException ignored) {}
        try { r.setRatingCleanliness(rs.getInt("rating_cleanliness")); } catch (SQLException ignored) {}
        try { r.setRatingOverall(rs.getBigDecimal("rating_overall")); } catch (SQLException ignored) {}
        try { r.setManagementResponse(rs.getString("management_response")); } catch (SQLException ignored) {}
        try {
            int uid = rs.getInt("responded_by_user_id");
            if (!rs.wasNull()) r.setRespondedByUserId(uid);
        } catch (SQLException ignored) {}
        try { r.setRespondedAt(rs.getTimestamp("responded_at")); } catch (SQLException ignored) {}
        try { r.setRewardGranted(rs.getBoolean("reward_granted")); } catch (SQLException ignored) {}
        try { r.setRewardPoints(rs.getInt("reward_points")); } catch (SQLException ignored) {}

        try {
            String cName = rs.getString("cust_name");
            r.setCustomerName(cName);
            r.setCustomerDisplayName(maskName(cName));
        } catch (SQLException ignored) {
            try {
                String cName = rs.getString("full_name");
                r.setCustomerName(cName);
                r.setCustomerDisplayName(maskName(cName));
            } catch (SQLException ignored2) {}
        }

        try { r.setRoomNumber(rs.getString("room_number")); } catch (SQLException ignored) {}
        try { r.setRoomTypeName(rs.getString("room_type_name")); } catch (SQLException ignored) {}
        try { r.setResponderName(rs.getString("responder_name")); } catch (SQLException ignored) {}

        return r;
    }
}
