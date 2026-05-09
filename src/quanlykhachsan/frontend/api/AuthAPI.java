package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.JsonUtil;

public class AuthAPI {

    public static User login(String username, String password) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", username);
        req.addProperty("password", password);

        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendPost("/auth/login", JsonUtil.getGson().toJson(req));
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused). Server đã được bật chưa?");
        }
        
        // Parse response
        JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null) {
            if ("success".equals(resObj.get("status").getAsString())) {
                JsonObject dataObj = resObj.getAsJsonObject("data");
                User user = new User();
                user.setId(dataObj.get("userId").getAsInt());
                user.setUsername(dataObj.get("username").getAsString());
                String roleStr = dataObj.get("role") != null ? dataObj.get("role").getAsString() : "USER";
                
                int roleId = 2; // Default Staff
                if ("ADMIN".equalsIgnoreCase(roleStr)) roleId = 1;
                else if ("CUSTOMER".equalsIgnoreCase(roleStr)) roleId = 3;
                user.setRoleId(roleId);
                if (dataObj.has("fullName")) user.setFullName(dataObj.get("fullName").getAsString());
                if (dataObj.has("email")) user.setEmail(dataObj.get("email").getAsString());
                if (dataObj.has("phone")) user.setPhone(dataObj.get("phone").getAsString());
                if (dataObj.has("customerId") && !dataObj.get("customerId").isJsonNull()) {
                    user.setCustomerId(dataObj.get("customerId").getAsInt());
                }
                return user;
            } else {
                throw new Exception(resObj.get("message").getAsString());
            }
        }
        throw new Exception("Phản hồi cấu trúc JSON từ máy chủ không xác định!");
    }

    public static String register(User user) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", user.getUsername());
        req.addProperty("password", user.getPassword());
        req.addProperty("fullName", user.getFullName());
        req.addProperty("email", user.getEmail());
        req.addProperty("phone", user.getPhone());

        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendPost("/auth/register", JsonUtil.getGson().toJson(req));
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused). Server đã được bật chưa?");
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
