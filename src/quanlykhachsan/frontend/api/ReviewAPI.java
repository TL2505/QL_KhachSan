package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.JsonUtil;

/**
 * Frontend API client for Reviews, Ratings, and Management Replies.
 */
public class ReviewAPI {
    private static final Gson gson = JsonUtil.getGson();

    public static List<Review> getReviewsByRoom(int roomId) {
        List<Review> list = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet(ServerConfig.getBaseUrl() + "/api/v1/reviews/room/" + roomId);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && ("success".equals(res.get("status").getAsString()) || res.get("success").getAsBoolean())) {
                JsonArray arr = res.getAsJsonArray("data");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        list.add(gson.fromJson(el, Review.class));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static List<Review> getAllReviews() {
        List<Review> list = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet(ServerConfig.getBaseUrl() + "/api/v1/reviews");
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && res.has("data") && res.get("data").isJsonArray()) {
                JsonArray arr = res.getAsJsonArray("data");
                for (JsonElement el : arr) {
                    list.add(gson.fromJson(el, Review.class));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static String addReview(Review review) {
        try {
            String json = HttpUtil.sendPost(ServerConfig.getBaseUrl() + "/api/v1/reviews", gson.toJson(review));
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null) {
                boolean success = res.has("success") && res.get("success").getAsBoolean();
                if (!success && res.has("status")) {
                    success = "success".equalsIgnoreCase(res.get("status").getAsString());
                }
                if (success) return "Success";
                if (res.has("message")) return res.get("message").getAsString();
            }
            return "Gửi đánh giá không thành công";
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    public static String replyReview(int reviewId, String reply) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("management_response", reply);
            String json = HttpUtil.sendPut(ServerConfig.getBaseUrl() + "/api/v1/reviews/" + reviewId + "/reply", gson.toJson(body));
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null) {
                boolean success = res.has("success") && res.get("success").getAsBoolean();
                if (!success && res.has("status")) {
                    success = "success".equalsIgnoreCase(res.get("status").getAsString());
                }
                if (success) return "Success";
                if (res.has("message")) return res.get("message").getAsString();
            }
            return "Không thể lưu phản hồi";
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    public static String deleteReview(int id) {
        try {
            String json = HttpUtil.sendDelete(ServerConfig.getBaseUrl() + "/api/v1/reviews/" + id);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null) {
                boolean success = res.has("success") && res.get("success").getAsBoolean();
                if (!success && res.has("status")) {
                    success = "success".equalsIgnoreCase(res.get("status").getAsString());
                }
                if (success) return "Success";
                if (res.has("message")) return res.get("message").getAsString();
            }
            return "Xóa thất bại";
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    public static JsonObject getPublicReputation(int page, int limit) {
        try {
            String json = HttpUtil.sendGet(ServerConfig.getBaseUrl() + "/api/v1/reviews/public?page=" + page + "&limit=" + limit);
            return gson.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
