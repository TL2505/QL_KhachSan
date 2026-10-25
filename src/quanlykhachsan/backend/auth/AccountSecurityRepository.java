package quanlykhachsan.backend.auth;

import java.sql.*;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.auth.AccountSecurityService.Account;

final class AccountSecurityRepository implements AccountSecurityService.Repository {
    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    AccountSecurityRepository() { this(DatabaseConnection::getConnection); }
    AccountSecurityRepository(Connections connections) { this.connections=connections; }
    public Account read(int id) throws Exception {
        try(var c=connections.open()) { return read(c,id); }
    }
    private Account read(Connection c,int id) throws SQLException {
        try(var q=c.prepareStatement("SELECT id,username,full_name,phone,email,verified_email,password,avatar_url FROM users WHERE id=? AND status='active'")) {
            q.setInt(1,id); try(var r=q.executeQuery()) { return r.next()?new Account(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8)):null; }
        }
    }
    public Account profile(int id,String name,String phone,String email) throws Exception {
        try(var c=connections.open()) {
            c.setAutoCommit(false);
            try {
                Integer customer=null;
                try(var q=c.prepareStatement("SELECT customer_id FROM users WHERE id=? AND status='active' FOR UPDATE")) {
                    q.setInt(1,id); try(var r=q.executeQuery()) { if(!r.next()) throw new AccountSecurityService.Failure(403,"Tài khoản không còn hoạt động."); customer=(Integer)r.getObject(1); }
                }
                try(var q=c.prepareStatement("UPDATE users SET full_name=?,phone=?,email=? WHERE id=?")) { q.setString(1,name);q.setString(2,phone);q.setString(3,email);q.setInt(4,id);q.executeUpdate(); }
                if(customer!=null) try(var q=c.prepareStatement("UPDATE customers SET full_name=?,phone=?,email=? WHERE id=?")) { q.setString(1,name);q.setString(2,phone);q.setString(3,email);q.setInt(4,customer);q.executeUpdate(); }
                Account result=read(c,id); c.commit(); return result;
            } catch(Exception e) {c.rollback();throw e;}
        }
    }
    public void verifyEmail(int id,String email) throws Exception {
        try(var c=connections.open();var q=c.prepareStatement("UPDATE users SET verified_email=? WHERE id=? AND LOWER(TRIM(email))=? AND status='active'")) {
            q.setString(1,email);q.setInt(2,id);q.setString(3,email);
            if(q.executeUpdate()!=1) throw new AccountSecurityService.Failure(409,"Email đã thay đổi. Hãy yêu cầu mã mới.");
        }
    }
    public void password(int id,String expectedHash,String email,String newHash) throws Exception {
        try(var c=connections.open();var q=c.prepareStatement("UPDATE users SET password=? WHERE id=? AND BINARY password=? AND LOWER(TRIM(email))=? AND LOWER(TRIM(verified_email))=? AND status='active'")) {
            q.setString(1,newHash);q.setInt(2,id);q.setString(3,expectedHash);q.setString(4,email);q.setString(5,email);
            if(q.executeUpdate()!=1) throw new AccountSecurityService.Failure(409,"Thông tin bảo mật đã thay đổi. Hãy yêu cầu mã mới.");
        }
    }
}
