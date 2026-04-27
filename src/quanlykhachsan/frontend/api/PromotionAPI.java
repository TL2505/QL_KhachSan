package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.promotion.Promotion;

public class PromotionAPI {
    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

    public static List<Promotion> getAllPromotions() {
        List<Promotion> list = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/promotions");
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                JsonArray data = res.getAsJsonArray("data");
                if (data != null) {
                    for (JsonElement el : data) {
                        list.add(gson.fromJson(el, Promotion.class));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public static List<Promotion> getActivePromotions() {
        List<Promotion> list = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/promotions/active");
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                JsonArray data = res.getAsJsonArray("data");
                if (data != null) {
                    for (JsonElement el : data) {
                        list.add(gson.fromJson(el, Promotion.class));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public static String createPromotion(Promotion p) {
         try {
            String body = gson.toJson(p);
            String json = HttpUtil.sendPost("/promotions", body);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return "Success";
            } else {
                return res != null && res.has("message") ? res.get("message").getAsString() : "Loi khong xac dinh";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Loi ket noi: " + e.getMessage();
        }
    }

    public static String updatePromotion(Promotion p) {
         try {
            String body = gson.toJson(p);
            String json = HttpUtil.sendPut("/promotions/" + p.getId(), body);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return "Success";
            } else {
                 return res != null && res.has("message") ? res.get("message").getAsString() : "Loi khong xac dinh";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Loi ket noi: " + e.getMessage();
        }
    }

    public static String deletePromotion(int id) {
        try {
            String json = HttpUtil.sendDelete("/promotions/" + id);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return "Success";
            } else {
                 return res != null && res.has("message") ? res.get("message").getAsString() : "Loi khong xac dinh";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Loi ket noi: " + e.getMessage();
        }
    }

    public static JsonObject getBestPromotionPreview(int bookingId) {
        try {
            String json = HttpUtil.sendGet("/promotions/best?bookingId=" + bookingId);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return res.getAsJsonObject("data");
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    /** Lấy xem trước khuyến mãi trước khi đặt phòng */
    public static JsonObject getPromotionPreview(int roomId, int customerId, String checkIn, String checkOut) {
        try {
            String query = String.format("?roomId=%d&customerId=%d&checkIn=%s&checkOut=%s",
                roomId, customerId, checkIn, checkOut);
            String json = HttpUtil.sendGet("/promotions/preview" + query);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return res.getAsJsonObject("data");
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
}
