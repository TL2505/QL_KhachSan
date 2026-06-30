package quanlykhachsan.backend.user;

import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.user.dto.UserCreateRequest;
import quanlykhachsan.backend.user.dto.UserResponse;

public class UserMapper {
    public static UserResponse toUserResponse(User user, String roleName) {
        if (user == null) return null;
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRoleId(user.getRoleId());
        response.setRoleName(roleName != null ? roleName : "");
        response.setStatus(user.getStatus());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setCustomerId(user.getCustomerId());
        return response;
    }

    public static User toUser(UserCreateRequest request) {
        if (request == null) return null;
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setRoleId(request.getRoleId());
        user.setStatus(request.getStatus());
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        return user;
    }
}
