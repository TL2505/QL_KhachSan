package quanlykhachsan.backend.interaction;

import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.backend.interaction.ReviewService;
import quanlykhachsan.backend.utils.SecurityUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.interaction.dto.ReviewCreateRequest;
import quanlykhachsan.backend.interaction.dto.ReviewResponse;
import quanlykhachsan.backend.interaction.ReviewMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ReviewController implements HttpHandler {
    private ReviewService reviewService = new ReviewService();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.startsWith("/api/reviews/room/")) {
                    // GET /api/reviews/room/{id}
                    int roomId = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
                    handleGetByRoom(exchange, roomId);
                } else {
                    // GET /api/reviews (Admin)
                    handleGetAll(exchange);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // POST /api/reviews
                handlePost(exchange);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                // DELETE /api/reviews/{id}
                int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
                handleDelete(exchange, id);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error(e.getMessage()));
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2))
            return;
        List<Review> list = reviewService.getAllReviews();
        List<ReviewResponse> dtoList = new java.util.ArrayList<>();
        for (Review r : list) {
            dtoList.add(ReviewMapper.toReviewResponse(r));
        }
        sendSuccess(exchange, dtoList);
    }

    private void handleGetByRoom(HttpExchange exchange, int roomId) throws IOException {
        List<Review> list = reviewService.getReviewsByRoom(roomId);
        List<ReviewResponse> dtoList = new java.util.ArrayList<>();
        for (Review r : list) {
            dtoList.add(ReviewMapper.toReviewResponse(r));
        }
        sendSuccess(exchange, dtoList);
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2, 3))
            return; // Customers can post

        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        ReviewCreateRequest req = gson.fromJson(body, ReviewCreateRequest.class);
        Review review = ReviewMapper.toReview(req);

        if (reviewService.addReview(review)) {
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Đánh giá thành công!"));
        } else {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Không thể lưu đánh giá!"));
        }
    }

    private void handleDelete(HttpExchange exchange, int id) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;
        if (reviewService.deleteReview(id)) {
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Đã xóa đánh giá!"));
        } else {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Xóa thất bại!"));
        }
    }

    private void sendSuccess(HttpExchange exchange, Object data) throws IOException {
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
    }
}
