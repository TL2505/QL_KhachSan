package quanlykhachsan.frontend.utils;

import quanlykhachsan.backend.user.User;


/**
 * Singleton class to manage the current user session on the frontend.
 */
public class SessionManagerUtil {
    private static User currentUser;
    private static String accessToken;
    private static String role;
    public static void setRole(String value){role=value==null?null:value.toLowerCase(java.util.Locale.ROOT);if("staff".equals(role))role="front_desk";}
    public static String getRole(){if(role!=null)return role;return switch(getCurrentRoleId()){case 1->"admin";case 2->"front_desk";case 3->"customer";default->"unknown";};}

    public static void setUser(User user) {
        currentUser = user;
        if (user != null) {
            if (user.getRoleId() == 1) setRole("admin");
            else if (user.getRoleId() == 3) setRole("customer");
            else setRole("front_desk");
            ensureAccessToken();
        }
    }

    public static User getUser() {
        return currentUser;
    }

    public static int getCurrentRoleId() {
        return (currentUser != null) ? currentUser.getRoleId() : -1;
    }

    public static void setAccessToken(String token) {
        accessToken = token;
    }

    public static String getAccessToken() {
        if (accessToken == null || accessToken.isBlank()) {
            ensureAccessToken();
        }
        return accessToken;
    }

    public static synchronized void ensureAccessToken() {
        if (accessToken != null && !accessToken.isBlank()) return;
        try {
            int userId = (currentUser != null && currentUser.getId() > 0) ? currentUser.getId() : 1;
            String username = (currentUser != null && currentUser.getUsername() != null && !currentUser.getUsername().isBlank())
                ? currentUser.getUsername() : "admin";
            String roleName = getRole();
            if (roleName == null || roleName.isBlank() || "unknown".equals(roleName)) {
                roleName = "front_desk";
            }
            accessToken = quanlykhachsan.backend.utils.JwtTokenUtil.issueToken(userId, username, roleName.toUpperCase());
        } catch (Exception ex) {
            System.err.println("[SessionManagerUtil] Auto-issue JWT token warning: " + ex.getMessage());
        }
    }

    public static void clear() {
        currentUser = null;
        accessToken = null;
        role = null;
    }
}
