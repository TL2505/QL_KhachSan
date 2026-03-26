package quanlykhachsan.backend.service;

import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.dao.UserDAO;

public class AuthService {

    private UserDAO userDAO = new UserDAO();

    public User login(String username, String password) {
        if (username == null || password == null) return null;

        User user = userDAO.findByUsername(username);

        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public boolean register(User user) {
        if (userDAO.findByUsername(user.getUsername()) != null) {
            return false; // trùng username
        }
        return userDAO.insert(user);
    }
}