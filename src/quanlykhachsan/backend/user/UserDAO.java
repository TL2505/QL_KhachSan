package quanlykhachsan.backend.user;

import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.user.Role;
import java.util.ArrayList;
import java.util.List;

public interface UserDAO {
    // ... logic existing
    public void addUser(User user) throws Exception;
    public void updateUser(User user) throws Exception;
    public void deleteUser(User user) throws Exception;
    public ArrayList<User> selectUser();
    public void comboBoxUser();
    public User findByUsername(String username);
    public boolean insert(User user) throws Exception;
    public int getRoleIdByName(String roleName);
    public List<Role> selectAllRoles();
}
