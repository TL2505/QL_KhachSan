package quanlykhachsan.backend.auth;

import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.user.UserDAO;
import quanlykhachsan.backend.user.UserDAOImpl;
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

    public boolean register(User user) throws Exception {
        if (userDAO.findByUsername(user.getUsername()) != null) {
            return false;
        }
        user.setPassword(SecurityUtil.hashPassword(user.getPassword()));
        return userDAO.insert(user);
    }

    public boolean registerCustomer(User user, quanlykhachsan.backend.customer.Customer customer) throws Exception {
        if (userDAO.findByUsername(user.getUsername()) != null) {
            throw new IllegalStateException("T\u00ean \u0111\u0103ng nh\u1eadp \u0111\u00e3 t\u1ed3n t\u1ea1i.");
        }

        quanlykhachsan.backend.customer.CustomerDAO customerDAO = new quanlykhachsan.backend.customer.CustomerDAOImpl();
        String identityCard = customer.getIdentityCard();
        try (java.sql.Connection connection = quanlykhachsan.backend.utils.DatabaseConnection.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int roleId;
                try (var query = connection.prepareStatement("SELECT id FROM roles WHERE LOWER(name)=?")) {
                    query.setString(1, "customer");
                    try (var rows = query.executeQuery()) {
                        if (!rows.next()) throw new IllegalStateException("Không tìm thấy phân quyền khách hàng trong cơ sở dữ liệu.");
                        roleId = rows.getInt(1);
                    }
                }

                int customerId = customerDAO.addAndReturnId(connection, customer);
                if (customerId <= 0) throw new java.sql.SQLException("Customer record was not created");
                if (identityCard != null && !identityCard.isBlank()) {
                    quanlykhachsan.backend.customer.GuestIdentityVaultDao.configured()
                        .save(connection, customerId, identityCard, null, null);
                }

                user.setCustomerId(customerId);
                user.setRoleId(roleId);
                user.setStatus("active");
                user.setPassword(SecurityUtil.hashPassword(user.getPassword()));
                boolean inserted = userDAO.insert(connection, user);
                if (!inserted) throw new java.sql.SQLException("User record was not created");
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof java.sql.SQLException sql && "23000".equals(sql.getSQLState())) {
                    String message = String.valueOf(sql.getMessage()).toLowerCase(java.util.Locale.ROOT);
                    if (message.contains("identity_hash")) throw new IllegalStateException("Số CCCD/Hộ chiếu đã được sử dụng.", e);
                    if (message.contains("username")) throw new IllegalStateException("Tên đăng nhập đã tồn tại.", e);
                }
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    public boolean updateProfile(String username, String fullName, String email, String phone) throws Exception {
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

    public boolean changePassword(String username, String oldPassword, String newPassword) throws Exception {
        User user = userDAO.findByUsername(username);
        if (user != null && SecurityUtil.verifyPassword(oldPassword, user.getPassword())) {
            user.setPassword(SecurityUtil.hashPassword(newPassword));
            userDAO.updateUser(user);
            return true;
        }
        return false;
    }

}
