package quanlykhachsan.backend.interaction;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import quanlykhachsan.backend.hotelservice.ServiceOrderController;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JsonUtil;

/**
 * Controller handling stay reviews, 4-criteria ratings, instant loyalty point allocation,
 * public reputation summaries, and management replies.
 */
public class ReviewController implements HttpHandler {

    private static final Pattern REPLY_PATH_PATTERN = Pattern.compile(".*/reviews/(\\d+)/reply/?$");
    private static final Pattern REVIEW_ID_PATH_PATTERN = Pattern.compile(".*/reviews/(\\d+)/?$");
    private static final Pattern ROOM_REVIEWS_PATH_PATTERN = Pattern.compile(".*/reviews/room/(\\d+)/?$");

    private static final Set<String> STAFF_ROLES = Set.of(
        "admin", "staff", "front_desk", "manager", "gm", "ADMIN", "STAFF", "1", "2"
    );

    private final ReviewService reviewService;
    public ReviewController() { this(new ReviewService()); }
    ReviewController(ReviewService reviewService) { this.reviewService = reviewService; }
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        if (path.endsWith("/public") && !"GET".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, OPTIONS");
            ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Public review summaries are read-only");
            return;
        }

        try {
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-User-Role");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            } else if ("GET".equalsIgnoreCase(method)) {
                if (path.endsWith("/public")) {
                    handleGetPublicSummary(exchange);
                } else {
                    Matcher roomMatcher = ROOM_REVIEWS_PATH_PATTERN.matcher(path);
                    if (roomMatcher.matches()) {
                        int roomId = Integer.parseInt(roomMatcher.group(1));
                        handleGetByRoom(exchange, roomId);
                    } else {
                        handleGetAll(exchange);
                    }
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                handleCreateReview(exchange);
            } else if ("PUT".equalsIgnoreCase(method)) {
                Matcher replyMatcher = REPLY_PATH_PATTERN.matcher(path);
                if (replyMatcher.matches()) {
                    int reviewId = Integer.parseInt(replyMatcher.group(1));
                    handleManagementReply(exchange, reviewId);
                } else {
                    ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Endpoint không tồn tại");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                Matcher idMatcher = REVIEW_ID_PATH_PATTERN.matcher(path);
                if (idMatcher.matches()) {
                    int reviewId = Integer.parseInt(idMatcher.group(1));
                    handleDelete(exchange, reviewId);
                } else {
                    ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Thiếu ID đánh giá cần xóa");
                }
            } else {
                ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
            }
        } catch (IllegalArgumentException e) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", e.getMessage());
        } catch (IllegalStateException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("ERR_NOT_CHECKED_OUT") || msg.contains("ERR_403")) {
                ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", msg);
            } else if (msg.contains("ERR_ALREADY_REVIEWED") || msg.contains("ERR_REVIEW_WINDOW_EXPIRED")) {
                ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", msg);
            } else {
                ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", msg);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || (e.getMessage() != null && e.getMessage().contains("uq_booking_review"))) {
                ApiResponseUtil.sendError(exchange, 400, "ERR_DUPLICATE_REVIEW", "Kỳ nghỉ này đã có đánh giá được gửi trước đó.");
            } else {
                e.printStackTrace();
                ApiResponseUtil.sendError(exchange, 500, "ERR_500_DATABASE", "Lỗi cơ sở dữ liệu: " + e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_INTERNAL", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    private void handleCreateReview(HttpExchange exchange) throws Exception {
        JsonObject req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
        if (req == null || !req.has("booking_id")) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Thiếu mã đặt phòng: booking_id");
            return;
        }

        int bookingId = req.get("booking_id").getAsInt();
        int ratingRoom = req.has("rating_room") ? req.get("rating_room").getAsInt() : 5;
        int ratingFnb = req.has("rating_fnb") ? req.get("rating_fnb").getAsInt() : 5;
        int ratingService = req.has("rating_service") ? req.get("rating_service").getAsInt() : 5;
        int ratingCleanliness = req.has("rating_cleanliness") ? req.get("rating_cleanliness").getAsInt() : 5;
        BigDecimal ratingOverall = null;
        if (req.has("rating_overall") && !req.get("rating_overall").isJsonNull()) {
            try {
                ratingOverall = req.get("rating_overall").getAsBigDecimal();
            } catch (Exception ignored) {}
        }
        String comment = req.has("comment") && !req.get("comment").isJsonNull() ? req.get("comment").getAsString().trim() : "";

        // IDOR Validation: Customer can only review their own booking
        int jwtCustomerId = ServiceOrderController.resolveCustomerId(exchange);
        String role = String.valueOf(exchange.getAttribute("role"));
        boolean isStaff = STAFF_ROLES.contains(role != null ? role.trim() : "");

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT customer_id, status FROM bookings WHERE id = ?")) {
            q.setInt(1, bookingId);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) {
                    ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Không tìm thấy mã đặt phòng #" + bookingId);
                    return;
                }
                int bookingOwnerId = r.getInt(1);
                String bookingStatus = r.getString(2);

                if (!isStaff && (jwtCustomerId <= 0 || bookingOwnerId != jwtCustomerId)) {
                    ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN",
                            "Từ chối quyền truy cập: Bạn không có quyền gửi đánh giá cho kỳ nghỉ này (IDOR Violation).");
                    return;
                }

                if (!"checked_out".equalsIgnoreCase(bookingStatus)) {
                    ApiResponseUtil.sendError(exchange, 403, "ERR_403_NOT_CHECKED_OUT",
                            "Đánh giá kỳ nghỉ chỉ áp dụng cho Quý khách đã hoàn tất trả phòng (Status: checked_out).");
                    return;
                }
            }
        }

        Review r = new Review();
        r.setBookingId(bookingId);
        r.setRatingRoom(ratingRoom);
        r.setRatingFnb(ratingFnb);
        r.setRatingService(ratingService);
        r.setRatingCleanliness(ratingCleanliness);
        r.setRatingOverall(ratingOverall);
        r.setComment(comment);

        Review created = reviewService.submitVerifiedReview(r);

        JsonObject data = new JsonObject();
        data.addProperty("review_id", created.getId());
        data.addProperty("booking_id", created.getBookingId());
        data.addProperty("room_number", created.getRoomNumber());
        data.addProperty("rating_overall", created.getRatingOverall());
        data.addProperty("reward_points", created.getRewardPoints());
        data.addProperty("created_at", System.currentTimeMillis());

        JsonObject envelope = new JsonObject();
        envelope.addProperty("success", true);
        envelope.addProperty("status", "success");
        envelope.addProperty("message", "Cảm ơn Quý khách đã gửi đánh giá! Quý khách đã được cộng ngay 200 điểm thưởng hội viên vào tài khoản.");
        envelope.add("data", data);

        ApiResponseUtil.write(exchange, 201, gson.toJson(envelope));
    }

    private void handleGetPublicSummary(HttpExchange exchange) throws Exception {
        String query = exchange.getRequestURI().getQuery();
        int page = 1;
        int limit = 6;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("page=")) {
                    try { page = Integer.parseInt(param.substring("page=".length())); } catch (NumberFormatException ignored) {}
                } else if (param.startsWith("limit=")) {
                    try { limit = Integer.parseInt(param.substring("limit=".length())); } catch (NumberFormatException ignored) {}
                }
            }
        }

        JsonObject responseObj = reviewService.getPublicReputationSummary(page, limit);
        ApiResponseUtil.write(exchange, 200, gson.toJson(responseObj));
    }

    private void handleManagementReply(HttpExchange exchange, int reviewId) throws Exception {
        String role = String.valueOf(exchange.getAttribute("role"));
        if (!STAFF_ROLES.contains(role != null ? role.trim() : "")) {
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Chức năng chỉ dành cho nhân viên hoặc quản lý khách sạn");
            return;
        }

        JsonObject req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
        if (req == null || !req.has("management_response")) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Thiếu nội dung phản hồi: management_response");
            return;
        }

        String reply = req.get("management_response").getAsString().trim();
        if (reply.isEmpty()) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Nội dung phản hồi không được để trống");
            return;
        }

        int userId = 0;
        Object uidObj = exchange.getAttribute("userId");
        if (uidObj == null) uidObj = exchange.getAttribute("user_id");
        if (uidObj != null) {
            try { userId = Integer.parseInt(uidObj.toString().trim()); } catch (NumberFormatException ignored) {}
        }

        boolean ok = reviewService.saveManagementReply(reviewId, reply, userId);
        if (ok) {
            JsonObject data = new JsonObject();
            data.addProperty("review_id", reviewId);
            data.addProperty("management_response", reply);
            data.addProperty("responded_at", System.currentTimeMillis());

            JsonObject envelope = new JsonObject();
            envelope.addProperty("success", true);
            envelope.addProperty("status", "success");
            envelope.addProperty("message", "Phản hồi của Ban Quản Lý đã được xuất bản công khai!");
            envelope.add("data", data);

            ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
        } else {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Không tìm thấy đánh giá #" + reviewId);
        }
    }

    private void handleGetAll(HttpExchange exchange) throws Exception {
        List<Review> list = reviewService.getAllReviews();
        JsonObject envelope = new JsonObject();
        envelope.addProperty("success", true);
        envelope.addProperty("status", "success");
        envelope.addProperty("total", list.size());
        envelope.add("data", gson.toJsonTree(list));
        ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
    }

    private void handleGetByRoom(HttpExchange exchange, int roomId) throws Exception {
        List<Review> list = reviewService.getReviewsByRoom(roomId);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("success", true);
        envelope.addProperty("status", "success");
        envelope.add("data", gson.toJsonTree(list));
        ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
    }

    private void handleDelete(HttpExchange exchange, int reviewId) throws Exception {
        String role = String.valueOf(exchange.getAttribute("role"));
        if (!STAFF_ROLES.contains(role != null ? role.trim() : "")) {
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Chỉ quản trị viên mới có quyền xóa đánh giá");
            return;
        }

        boolean ok = reviewService.deleteReview(reviewId);
        if (ok) {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("success", true);
            envelope.addProperty("status", "success");
            envelope.addProperty("message", "Đã xóa đánh giá thành công");
            ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
        } else {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Không tìm thấy đánh giá để xóa");
        }
    }
}
