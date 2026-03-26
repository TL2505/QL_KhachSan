package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.User;
import java.util.ArrayList;

public interface UserDAO {

//    add User
    public void addUser(User user);

//    update User
    public void updateUser(User user);

//    delete User
    public void deleteUser(User user);

//    list of User
    public ArrayList<User> selectUser();

    public void comboBoxUser();

    public User findByUsername(String username);
    public boolean insert(User user);
}
