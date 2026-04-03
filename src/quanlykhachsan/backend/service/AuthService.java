package quanlykhachsan.backend.service;

import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.dao.UserDAO;
import quanlykhachsan.backend.daoimpl.UserDAOImpl;

public class AuthService {

    private UserDAO userDAO = new UserDAOImpl();

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
            return false;
        }
        return userDAO.insert(user);
    }
}