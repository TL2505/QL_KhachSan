package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.frontend.utils.HttpUtil;

public class UserAPI {

    public static String updateProfile(String username, String fullName, String email, String phone) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", username);
        if (fullName != null) req.addProperty("fullName", fullName);
        if (email != null) req.addProperty("email", email);
        if (phone != null) req.addProperty("phone", phone);

        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendPost("/users/update-profile", new Gson().toJson(req));
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null) {
            if ("success".equals(resObj.get("status").getAsString())) {
                return resObj.get("message").getAsString();
            } else {
                throw new Exception(resObj.get("message").getAsString());
            }
        }
        throw new Exception("Phản hồi cấu trúc JSON từ máy chủ không xác định!");
    }

    public static String changePassword(String username, String oldPassword, String newPassword) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", username);
        req.addProperty("oldPassword", oldPassword);
        req.addProperty("newPassword", newPassword);

        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendPost("/users/change-password", new Gson().toJson(req));
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null) {
            if ("success".equals(resObj.get("status").getAsString())) {
                return resObj.get("message").getAsString();
            } else {
                throw new Exception(resObj.get("message").getAsString());
            }
        }
        throw new Exception("Phản hồi cấu trúc JSON từ máy chủ không xác định!");
    }
}
