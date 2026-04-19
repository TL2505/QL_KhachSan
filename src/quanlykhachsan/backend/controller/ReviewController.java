package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.Review;
import quanlykhachsan.backend.service.ReviewService;
import quanlykhachsan.backend.utils.SecurityUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;

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
            sendJson(exchange, 500, "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2))
            return;
        List<Review> list = reviewService.getAllReviews();
        sendSuccess(exchange, list);
    }

    private void handleGetByRoom(HttpExchange exchange, int roomId) throws IOException {
        List<Review> list = reviewService.getReviewsByRoom(roomId);
        sendSuccess(exchange, list);
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2, 3))
            return; // Customers can post

        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Review review = gson.fromJson(body, Review.class);

        if (reviewService.addReview(review)) {
            sendJson(exchange, 200, "{\"status\": \"success\", \"message\": \"Đánh giá thành công!\"}");
        } else {
            sendJson(exchange, 500, "{\"status\": \"error\", \"message\": \"Không thể lưu đánh giá!\"}");
        }
    }

    private void handleDelete(HttpExchange exchange, int id) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;
        if (reviewService.deleteReview(id)) {
            sendJson(exchange, 200, "{\"status\": \"success\", \"message\": \"Đã xóa đánh giá!\"}");
        } else {
            sendJson(exchange, 500, "{\"status\": \"error\", \"message\": \"Xóa thất bại!\"}");
        }
    }

    private void sendSuccess(HttpExchange exchange, Object data) throws IOException {
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(data));
        sendJson(exchange, 200, res.toString());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
