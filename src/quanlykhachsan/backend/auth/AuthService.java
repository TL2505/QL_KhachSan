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
        System.out.println("DEBUG: Đang đăng ký khách hàng mới [Service]...");
        System.out.println("  - Username: " + user.getUsername());
        System.out.println("  - IdentityCard: " + customer.getIdentityCard());

        if (userDAO.findByUsername(user.getUsername()) != null) {
            System.out.println("  - [FAIL] Username đã tồn tại!");
            throw new IllegalStateException("T\u00ean \u0111\u0103ng nh\u1eadp \u0111\u00e3 t\u1ed3n t\u1ea1i.");
        }
        
        quanlykhachsan.backend.customer.CustomerDAO customerDAO = new quanlykhachsan.backend.customer.CustomerDAOImpl();
        String identityCard = customer.getIdentityCard();
        if (identityCard != null && !identityCard.isBlank()) {
            boolean identityAlreadyUsed = customerDAO.findAll().stream()
                .map(quanlykhachsan.backend.customer.Customer::getIdentityCard)
                .filter(java.util.Objects::nonNull)
                .anyMatch(identityCard.trim()::equals);
            if (identityAlreadyUsed) {
                throw new IllegalStateException("S\u1ed1 CCCD/H\u1ed9 chi\u1ebfu \u0111\u00e3 \u0111\u01b0\u1ee3c s\u1eed d\u1ee5ng.");
            }
        }
        System.out.println("  - [STEP] Đang tạo hồ sơ khách hàng...");
        int customerId = customerDAO.addAndReturnId(customer);
        
        if (customerId > 0) {
            System.out.println("  - [SUCCESS] Tạo customer thành công ID: " + customerId);
            user.setCustomerId(customerId);
            
            int roleId = userDAO.getRoleIdByName("customer");
            if (roleId == -1) {
                System.out.println("  - [FAIL] Không tìm thấy quyền 'customer' trong bảng roles!");
                throw new Exception("Lỗi hệ thống: Không tìm thấy phân quyền khách hàng trong Database!");
            }
            user.setRoleId(roleId);
            user.setStatus("active");
            user.setPassword(SecurityUtil.hashPassword(user.getPassword()));
            
            System.out.println("  - [STEP] Đang tạo tài khoản người dùng với RoleID: " + roleId);
            boolean ok = userDAO.insert(user);
            if (ok) System.out.println("  - [DONE] Đăng ký hoàn tất.");
            else System.out.println("  - [FAIL] Lỗi khi insert User!");
            return ok;
        }
        System.out.println("  - [FAIL] Không tạo được hồ sơ khách hàng (ID <= 0)");
        return false;
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

    public User autoProvisionSocialUser(String provider, String providerId, String email, String name) throws Exception {
        String cleanEmail = (email != null && !email.isBlank()) ? email.trim().toLowerCase(java.util.Locale.ROOT) : (providerId + "@" + provider.toLowerCase() + ".com");
        String displayName = (name != null && !name.isBlank()) ? name.trim() : (provider.substring(0, 1).toUpperCase() + provider.substring(1).toLowerCase() + " Member");

        try (java.sql.Connection conn = quanlykhachsan.backend.utils.DBconn.getConnection()) {
            User existingUser = null;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, username, password, role_id, status, full_name, email, phone, customer_id " +
                    "FROM users WHERE email = ? OR (auth_provider = ? AND provider_id = ?) LIMIT 1")) {
                ps.setString(1, cleanEmail);
                ps.setString(2, provider);
                ps.setString(3, providerId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        existingUser = new User();
                        existingUser.setId(rs.getInt("id"));
                        existingUser.setUsername(rs.getString("username"));
                        existingUser.setPassword(rs.getString("password"));
                        existingUser.setRoleId(rs.getInt("role_id"));
                        existingUser.setStatus(rs.getString("status"));
                        existingUser.setFullName(rs.getString("full_name"));
                        existingUser.setEmail(rs.getString("email"));
                        existingUser.setPhone(rs.getString("phone"));
                        int cid = rs.getInt("customer_id");
                        if (!rs.wasNull()) existingUser.setCustomerId(cid);
                    }
                }
            }

            if (existingUser != null) {
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET auth_provider = ?, provider_id = ? WHERE id = ?")) {
                    ps.setString(1, provider);
                    ps.setString(2, providerId);
                    ps.setInt(3, existingUser.getId());
                    ps.executeUpdate();
                }
                if (existingUser.getCustomerId() > 0) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "UPDATE customers SET auth_provider = ?, provider_id = ? WHERE id = ?")) {
                        ps.setString(1, provider);
                        ps.setString(2, providerId);
                        ps.setInt(3, existingUser.getCustomerId());
                        ps.executeUpdate();
                    }
                }
                return existingUser;
            }

            // Create customer record in customers table
            int customerId = -1;
            String idCard = "SOC-" + provider.substring(0, Math.min(2, provider.length())).toUpperCase() + "-" + (System.currentTimeMillis() % 100000000);
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO customers (full_name, identity_card, phone, email, is_vip, loyalty_points, total_loyalty_points, loyalty_level, auth_provider, provider_id) " +
                    "VALUES (?, ?, '', ?, FALSE, 0, 0, 'Member', ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, displayName);
                ps.setString(2, idCard);
                ps.setString(3, cleanEmail);
                ps.setString(4, provider);
                ps.setString(5, providerId);
                ps.executeUpdate();
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        customerId = rs.getInt(1);
                    }
                }
            }

            // Strictly assign role_id = 3 (customer) - Never grant admin/staff privileges
            int roleId = userDAO.getRoleIdByName("customer");
            if (roleId <= 0) roleId = 3;

            String randomPass = java.util.UUID.randomUUID().toString();
            String hashedPass = SecurityUtil.hashPassword(randomPass);

            User newUser = new User();
            newUser.setUsername(cleanEmail);
            newUser.setPassword(hashedPass);
            newUser.setRoleId(roleId);
            newUser.setStatus("active");
            newUser.setFullName(displayName);
            newUser.setEmail(cleanEmail);
            newUser.setPhone("");
            newUser.setCustomerId(customerId);

            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password, role_id, status, full_name, email, phone, customer_id, auth_provider, provider_id) " +
                    "VALUES (?, ?, ?, 'active', ?, ?, '', ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, cleanEmail);
                ps.setString(2, hashedPass);
                ps.setInt(3, roleId);
                ps.setString(4, displayName);
                ps.setString(5, cleanEmail);
                ps.setInt(6, customerId);
                ps.setString(7, provider);
                ps.setString(8, providerId);
                ps.executeUpdate();
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        newUser.setId(rs.getInt(1));
                    }
                }
            }
            return newUser;
        }
    }

}
