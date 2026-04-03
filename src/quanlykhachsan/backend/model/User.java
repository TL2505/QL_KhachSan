package quanlykhachsan.backend.model;

public class User {
    private int id;
    private String username;
    private String password;
    private int roleId;
    private String status;

    public User() {
    }

    public User(int id, String username, String password, int roleId, String status) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.roleId = roleId;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
