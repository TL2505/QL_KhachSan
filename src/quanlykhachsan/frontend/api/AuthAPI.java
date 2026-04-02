package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.utils.HttpUtil;

public class AuthAPI {

    public static User login(String username, String password) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", username);
        req.addProperty("password", password);

        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendPost("/auth/login", new Gson().toJson(req));
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused). Server đã được bật chưa?");
        }
        
        // Parse response
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null) {
            if ("success".equals(resObj.get("status").getAsString())) {
                JsonObject dataObj = resObj.getAsJsonObject("data");
                User user = new User();
                user.setId(dataObj.get("userId").getAsInt());
                user.setUsername(dataObj.get("username").getAsString());
                String roleStr = dataObj.get("role") != null ? dataObj.get("role").getAsString() : "USER";
                user.setRoleId("ADMIN".equalsIgnoreCase(roleStr) ? 1 : 2);
                if (dataObj.has("fullName")) user.setFullName(dataObj.get("fullName").getAsString());
                if (dataObj.has("email")) user.setEmail(dataObj.get("email").getAsString());
                if (dataObj.has("phone")) user.setPhone(dataObj.get("phone").getAsString());
                return user;
            } else {
                throw new Exception(resObj.get("message").getAsString());
            }
        }
        throw new Exception("Phản hồi cấu trúc JSON từ máy chủ không xác định!");
    }
}
