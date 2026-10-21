package quanlykhachsan.backend.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in integration uses only its own disposable schema, never hotel data. */
class AccountSecurityDatabaseTest {
    @Test void profileSynchronizationRollbackAndConditionalSecurityWrites()throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("HOTEL_RUN_ACCOUNT_DB_TESTS")));
        String schema="hotel_account_"+UUID.randomUUID().toString().replace("-","")+"_test";
        String server="jdbc:mysql://127.0.0.1:3306/";
        try(var admin=DriverManager.getConnection(server,"root","");var setup=admin.createStatement()){
            setup.executeUpdate("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                try(var c=DriverManager.getConnection(server+schema,"root","");var s=c.createStatement()){
                    s.executeUpdate("CREATE TABLE customers (id INT PRIMARY KEY,full_name VARCHAR(100),phone VARCHAR(20),email VARCHAR(100)) ENGINE=InnoDB");
                    s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY,username VARCHAR(50),full_name VARCHAR(100),phone VARCHAR(20),email VARCHAR(100),verified_email VARCHAR(100),password VARCHAR(255),customer_id INT,status VARCHAR(20)) ENGINE=InnoDB");
                    s.executeUpdate("INSERT INTO customers VALUES(7,'Guest','0900000000','old@example.invalid')");
                    s.executeUpdate("INSERT INTO users VALUES(1,'guest','Guest','0900000000','old@example.invalid',NULL,'OriginalHash',7,'active')");
                    s.executeUpdate("CREATE TRIGGER fail_customer BEFORE UPDATE ON customers FOR EACH ROW BEGIN IF NEW.full_name='force-rollback' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'; END IF; END");
                }
                var repo=new AccountSecurityRepository(()->DriverManager.getConnection(server+schema,"root",""));
                assertFalse(repo.read(1).emailVerified());
                repo.verifyEmail(1,"old@example.invalid");assertTrue(repo.read(1).emailVerified());
                repo.profile(1,"Nguyễn Ánh","0901234567","new@example.invalid");
                assertFalse(repo.read(1).emailVerified());
                try(var c=DriverManager.getConnection(server+schema,"root","");var s=c.createStatement();var r=s.executeQuery("SELECT full_name,email FROM customers WHERE id=7")){
                    assertTrue(r.next());assertEquals("Nguyễn Ánh",r.getString(1));assertEquals("new@example.invalid",r.getString(2));
                }
                assertThrows(SQLException.class,()->repo.profile(1,"force-rollback","","bad@example.invalid"));
                assertEquals("Nguyễn Ánh",repo.read(1).fullName());assertEquals("new@example.invalid",repo.read(1).email());
                assertThrows(AccountSecurityService.Failure.class,()->repo.verifyEmail(1,"old@example.invalid"));
                assertThrows(AccountSecurityService.Failure.class,()->repo.password(1,"OriginalHash","new@example.invalid","NewHash"));
                repo.verifyEmail(1,"new@example.invalid");
                assertThrows(AccountSecurityService.Failure.class,()->repo.password(1,"originalhash","new@example.invalid","NewHash"));
                repo.password(1,"OriginalHash","new@example.invalid","NewHash");
                assertEquals("NewHash",repo.read(1).passwordHash());
                assertThrows(AccountSecurityService.Failure.class,()->repo.password(1,"OriginalHash","new@example.invalid","StaleHash"));
                try(var c=DriverManager.getConnection(server+schema,"root","");var s=c.createStatement()){
                    s.executeUpdate("UPDATE users SET email='legacy-change@example.invalid' WHERE id=1");
                }
                assertFalse(repo.read(1).emailVerified(),"Existing update paths cannot carry verification to a different email");
                assertThrows(AccountSecurityService.Failure.class,()->repo.password(1,"NewHash","new@example.invalid","StaleHash"));
            } finally {setup.executeUpdate("DROP DATABASE "+schema);}
        }
    }
}
