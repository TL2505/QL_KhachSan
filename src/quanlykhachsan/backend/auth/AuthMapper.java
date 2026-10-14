package quanlykhachsan.backend.auth;

import quanlykhachsan.backend.auth.dto.AuthResponse;
import quanlykhachsan.backend.auth.dto.AuthUserInfo;
import quanlykhachsan.backend.user.User;

public class AuthMapper {

    public static AuthResponse success(String message, Object data) {
        AuthResponse response = new AuthResponse();
        response.setStatus("success");
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static AuthResponse error(String message) {
        AuthResponse response = new AuthResponse();
        response.setStatus("error");
        response.setMessage(message);
        response.setData(null);
        return response;
    }

    public static AuthUserInfo toUserInfo(User user, String roleStr) {
        AuthUserInfo info = new AuthUserInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setRole(roleStr);
        info.setFullName(user.getFullName() != null ? user.getFullName() : "");
        info.setEmail(user.getEmail() != null ? user.getEmail() : "");
        info.setPhone(user.getPhone() != null ? user.getPhone() : "");
        info.setCustomerId(user.getCustomerId());
        return info;
    }
}
