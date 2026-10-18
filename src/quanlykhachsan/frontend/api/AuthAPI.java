package quanlykhachsan.frontend.api;

import com.google.gson.JsonObject;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.JsonUtil;
import quanlykhachsan.frontend.utils.SessionManagerUtil;

public class AuthAPI {

    public static User login(String username, String password) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", username);
        req.addProperty("password", password);

        java.net.http.HttpResponse<String> resp;
        try {
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("Content-Type", "application/json");
            resp = ApiClient.getInstance().sendSync("POST", "/api/v1/auth/login", JsonUtil.getGson().toJson(req), headers);
        } catch (java.net.ConnectException ex) {
            throw new Exception("Server chưa up");
        } catch (java.io.IOException ex) {
            throw new Exception("Không thể kết nối đến máy chủ: " + ex.getMessage());
        }

        if (resp == null) {
            throw new Exception("Máy chủ phản hồi dữ liệu không hợp lệ. Vui lòng kiểm tra lại kết nối hoặc URL máy chủ.");
        }

        int statusCode = resp.statusCode();
        String jsonResponse = resp.body();

        if (statusCode == 401 || statusCode == 403) {
            throw new Exception("Tên đăng nhập hoặc mật khẩu không chính xác!");
        }

        if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.trim().startsWith("<")) {
            throw new Exception("Máy chủ phản hồi dữ liệu không hợp lệ. Vui lòng kiểm tra lại kết nối hoặc URL máy chủ.");
        }

        JsonObject resObj;
        try {
            resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        } catch (Exception e) {
            throw new Exception("Máy chủ phản hồi dữ liệu không hợp lệ. Vui lòng kiểm tra lại kết nối hoặc URL máy chủ.");
        }

        if (resObj != null && resObj.has("status")) {
            if ("success".equals(resObj.get("status").getAsString())) {
                JsonObject dataObj = resObj.getAsJsonObject("data");
                String token = null;
                if (dataObj != null) {
                    if (dataObj.has("accessToken") && !dataObj.get("accessToken").isJsonNull()) {
                        token = dataObj.get("accessToken").getAsString();
                    } else if (dataObj.has("token") && !dataObj.get("token").isJsonNull()) {
                        token = dataObj.get("token").getAsString();
                    } else if (dataObj.has("access_token") && !dataObj.get("access_token").isJsonNull()) {
                        token = dataObj.get("access_token").getAsString();
                    }
                }
                if (token == null || token.isBlank()) {
                    if (resObj.has("accessToken") && !resObj.get("accessToken").isJsonNull()) {
                        token = resObj.get("accessToken").getAsString();
                    } else if (resObj.has("token") && !resObj.get("token").isJsonNull()) {
                        token = resObj.get("token").getAsString();
                    } else if (resObj.has("access_token") && !resObj.get("access_token").isJsonNull()) {
                        token = resObj.get("access_token").getAsString();
                    }
                }

                if (token != null && !token.isBlank()) {
                    SessionManagerUtil.setAccessToken(token);
                }

                User user = new User();
                if (dataObj != null) {
                    if (dataObj.has("userId")) user.setId(dataObj.get("userId").getAsInt());
                    else if (dataObj.has("id")) user.setId(dataObj.get("id").getAsInt());
                    if (dataObj.has("username")) user.setUsername(dataObj.get("username").getAsString());
                    String roleStr = dataObj.has("role") && !dataObj.get("role").isJsonNull() ? dataObj.get("role").getAsString() : "USER";

                    int roleId = 2; // Default Staff
                    if ("ADMIN".equalsIgnoreCase(roleStr)) roleId = 1;
                    else if ("CUSTOMER".equalsIgnoreCase(roleStr)) roleId = 3;
                    user.setRoleId(roleId);
                    if (dataObj.has("roleId")) user.setRoleId(dataObj.get("roleId").getAsInt());
                    SessionManagerUtil.setRole(roleStr);
                    if (dataObj.has("fullName")) user.setFullName(dataObj.get("fullName").getAsString());
                    if (dataObj.has("email")) user.setEmail(dataObj.get("email").getAsString());
                    if (dataObj.has("phone")) user.setPhone(dataObj.get("phone").getAsString());
                    if (dataObj.has("customerId") && !dataObj.get("customerId").isJsonNull()) {
                        user.setCustomerId(dataObj.get("customerId").getAsInt());
                    }
                }

                SessionManagerUtil.setUser(user);
                return user;
            } else {
                String errMsg = resObj.has("message") ? resObj.get("message").getAsString() : "Đăng nhập thất bại";
                if (errMsg.contains("token") || errMsg.contains("Token") || errMsg.contains("Sai tên đăng nhập") || errMsg.contains("expired")) {
                    throw new Exception("Tên đăng nhập hoặc mật khẩu không chính xác!");
                }
                throw new Exception(errMsg);
            }
        }
        throw new Exception("Server chưa up");
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
            throw new Exception("Server chưa up");
        }
        
        if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.trim().startsWith("<")) {
            throw new Exception("Máy chủ phản hồi dữ liệu không hợp lệ. Vui lòng kiểm tra lại kết nối hoặc URL máy chủ.");
        }

        JsonObject resObj;
        try {
            resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        } catch (Exception e) {
            throw new Exception("Máy chủ phản hồi dữ liệu không hợp lệ. Vui lòng kiểm tra lại kết nối hoặc URL máy chủ.");
        }

        if (resObj != null && resObj.has("status")) {
            if ("success".equals(resObj.get("status").getAsString())) {
                return resObj.get("message").getAsString();
            } else {
                throw new Exception(resObj.get("message").getAsString());
            }
        }
        throw new Exception("Server chưa up");
    }
}
