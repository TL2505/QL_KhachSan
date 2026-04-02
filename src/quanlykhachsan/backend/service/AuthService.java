package quanlykhachsan.backend.service;

import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.dao.UserDAO;
import quanlykhachsan.backend.daoimpl.UserDAOImpl;
import quanlykhachsan.backend.utils.SecurityUtil;

public class AuthService {

    private UserDAO userDAO = new UserDAOImpl();

    public User login(String username, String password) {
        if (username == null || password == null) return null;

        User user = userDAO.findByUsername(username);

        if (user != null && SecurityUtil.verifyPassword(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public boolean register(User user) {
        if (userDAO.findByUsername(user.getUsername()) != null) {
            return false;
        }
        user.setPassword(SecurityUtil.hashPassword(user.getPassword()));
        return userDAO.insert(user);
    }

    public boolean updateProfile(String username, String fullName, String email, String phone) {
        User user = userDAO.findByUsername(username);
        if (user != null) {
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPhone(phone);
            userDAO.updateUser(user);
            return true;
        }
        return false;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = userDAO.findByUsername(username);
        if (user != null && SecurityUtil.verifyPassword(oldPassword, user.getPassword())) {
            user.setPassword(SecurityUtil.hashPassword(newPassword));
            userDAO.updateUser(user);
            return true;
        }
        return false;
    }
}