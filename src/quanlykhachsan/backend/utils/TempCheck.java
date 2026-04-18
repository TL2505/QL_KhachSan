package quanlykhachsan.backend.utils;

public class TempCheck {
    public static void main(String[] args) {
        System.out.println("Result: " + quanlykhachsan.backend.utils.SecurityUtil.verifyPassword("123456", "$2a$10$EUiC6sIZD/Un75n20QIKjO3r5xP2eVJ4AB0ZNr/E0guhBY5GyzbFG"));
        System.out.println("New Hash: " + quanlykhachsan.backend.utils.SecurityUtil.hashPassword("123456"));
    }
}
