package quanlykhachsan.frontend.utils;

import quanlykhachsan.backend.model.User;

import quanlykhachsan.frontend.utils.ThemeManager;

/**
 * Singleton class to manage the current user session on the frontend.
 */
public class SessionManagerUtil {
    private static User currentUser;

    public static void setUser(User user) {
        currentUser = user;
        // Theme preference is now managed locally on the machine, ignoring DB preference.
    }

    public static User getUser() {
        return currentUser;
    }

    public static int getCurrentRoleId() {
        return (currentUser != null) ? currentUser.getRoleId() : -1;
    }

    public static void clear() {
        currentUser = null;
    }
}
