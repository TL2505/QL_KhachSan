package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.model.Role;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Type;

public class UserAPI {

    public static List<Role> getRoles() throws Exception {
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendGet("/roles");
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server!");
        }
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            Type listType = new TypeToken<ArrayList<Role>>(){}.getType();
            return new Gson().fromJson(resObj.get("data"), listType);
        }
        throw new Exception("Lỗi khi tải danh sách quyền!");
    }

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


    public static List<User> getAllUsers() throws Exception {
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendGet("/users");
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            Type listType = new TypeToken<ArrayList<User>>(){}.getType();
            return new Gson().fromJson(resObj.get("data"), listType);
        }
        throw new Exception("Lỗi khi tải danh sách người dùng!");
    }

    public static String createUser(User user) throws Exception {
        String jsonBody = new Gson().toJson(user);
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendPost("/users", jsonBody);
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            return resObj.get("message").getAsString();
        } else if (resObj != null) {
            throw new Exception(resObj.get("message").getAsString());
        }
        throw new Exception("Lỗi khi thêm người dùng!");
    }

    public static String updateUser(User user) throws Exception {
        String jsonBody = new Gson().toJson(user);
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendPut("/users/" + user.getId(), jsonBody);
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            return resObj.get("message").getAsString();
        } else if (resObj != null) {
            throw new Exception(resObj.get("message").getAsString());
        }
        throw new Exception("Lỗi khi cập nhật người dùng!");
    }

    public static String deleteUser(int userId) throws Exception {
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendDelete("/users/" + userId);
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            return resObj.get("message").getAsString();
        } else if (resObj != null) {
            throw new Exception(resObj.get("message").getAsString());
        }
        throw new Exception("Lỗi khi xóa người dùng!");
    }
}
