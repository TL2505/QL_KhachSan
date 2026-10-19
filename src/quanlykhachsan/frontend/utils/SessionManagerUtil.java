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
            else if(role==null)setRole("front_desk");
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
        if (currentUser != null && (accessToken == null || accessToken.isBlank())) {
            ensureAccessToken();
        }
        return accessToken;
    }

    /** Credentials come exclusively from the authenticated backend login response. */
    public static synchronized void ensureAccessToken() { }

    public static void clear() {
        currentUser = null;
        accessToken = null;
        role = null;
    }
}
