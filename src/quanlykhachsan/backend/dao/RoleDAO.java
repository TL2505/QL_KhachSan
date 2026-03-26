package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Role;
import java.util.ArrayList;

public interface RoleDAO {

//    add Role
    public void addRole(Role role);

//    update Role
    public void updateRole(Role role);

//    delete Role
    public void deleteRole(Role role);

//    list of Role
    public ArrayList<Role> selectRole();

    public void comboBoxRole();

}
