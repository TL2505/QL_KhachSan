package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.JsonUtil;

import java.util.ArrayList;
import java.util.List;

public class ReviewAPI {
    private static final Gson gson = JsonUtil.getGson();

    public static List<Review> getReviewsByRoom(int roomId) {
        List<Review> list = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/reviews/room/" + roomId);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                JsonArray arr = res.getAsJsonArray("data");
                for (JsonElement el : arr) {
                    list.add(gson.fromJson(el, Review.class));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public static List<Review> getAllReviews() {
        List<Review> list = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/reviews");
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                JsonArray arr = res.getAsJsonArray("data");
                for (JsonElement el : arr) {
                    list.add(gson.fromJson(el, Review.class));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public static String addReview(Review review) {
        try {
            String json = HttpUtil.sendPost("/reviews", gson.toJson(review));
            JsonObject res = gson.fromJson(json, JsonObject.class);
            return res.get("status").getAsString().equals("success") ? "Success" : res.get("message").getAsString();
        } catch (Exception e) {
            return "Exception: " + e.getMessage();
        }
    }

    public static String deleteReview(int id) {
        try {
            String json = HttpUtil.sendDelete("/reviews/" + id);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            return res.get("status").getAsString().equals("success") ? "Success" : res.get("message").getAsString();
        } catch (Exception e) {
            return "Exception: " + e.getMessage();
        }
    }
}
