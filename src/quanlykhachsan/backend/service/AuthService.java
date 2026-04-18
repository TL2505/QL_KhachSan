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

    public boolean register(User user) throws Exception {
        if (userDAO.findByUsername(user.getUsername()) != null) {
            return false;
        }
        user.setPassword(SecurityUtil.hashPassword(user.getPassword()));
        return userDAO.insert(user);
    }

    public boolean registerCustomer(User user, quanlykhachsan.backend.model.Customer customer) throws Exception {
        System.out.println("DEBUG: Đang đăng ký khách hàng mới [Service]...");
        System.out.println("  - Username: " + user.getUsername());
        System.out.println("  - IdentityCard: " + customer.getIdentityCard());

        if (userDAO.findByUsername(user.getUsername()) != null) {
            System.out.println("  - [FAIL] Username đã tồn tại!");
            return false;
        }
        
        quanlykhachsan.backend.dao.CustomerDAO customerDAO = new quanlykhachsan.backend.daoimpl.CustomerDAOImpl();
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

}