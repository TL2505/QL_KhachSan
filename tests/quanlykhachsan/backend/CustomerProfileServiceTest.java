package quanlykhachsan.backend;

import com.google.gson.JsonObject;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import quanlykhachsan.backend.customer.*;
import quanlykhachsan.backend.utils.AesGcmEncryptionUtil;
import static org.junit.jupiter.api.Assertions.*;

class CustomerProfileServiceTest {
    private JsonObject input() {
        var data=new JsonObject();data.addProperty("fullName","SYNTHETIC SCAN");data.addProperty("phone","0900000000");
        data.addProperty("email","synthetic@example.invalid");data.addProperty("identityCard","000000000000");
        data.addProperty("dateOfBirth","2000-01-01");data.addProperty("address","SYNTHETIC ADDRESS");
        return data;
    }
    @Test void validatesDatesAndIdentityWithoutTrustingClientIds() {
        var data=input();data.addProperty("customerId",999);assertEquals("000000000000",CustomerProfileService.validate(data).identityCard());
        data.addProperty("dateOfBirth","2000-02-31");assertThrows(IllegalArgumentException.class,()->CustomerProfileService.validate(data));
        data.addProperty("dateOfBirth","2000-01-01");data.addProperty("identityCard","bad");assertThrows(IllegalArgumentException.class,()->CustomerProfileService.validate(data));
    }
    @Test void writesOnlyLinkedCustomerEncryptsIdentityAndRollsBackConflicts() throws Exception {
        String url=System.getenv("HOTEL_PROFILE_TEST_URL");Assumptions.assumeTrue(url!=null,"Requires an explicit local disposable-schema test connection");
        String schema="profile_"+UUID.randomUUID().toString().replace("-","")+"_test";
        try(var c=DriverManager.getConnection(url,"root","");var setup=c.createStatement()) {
            setup.execute("CREATE DATABASE `"+schema+"`");
            try {
                c.setCatalog(schema);
                try(var s=c.createStatement()) {
                    s.execute("CREATE TABLE customers(id INT PRIMARY KEY,full_name VARCHAR(100),phone VARCHAR(20),email VARCHAR(100),address VARCHAR(255),date_of_birth DATE,identity_card VARCHAR(20) NULL CHECK(identity_card IS NULL),identity_hash BINARY(32) UNIQUE)");
                    s.execute("CREATE TABLE users(id INT PRIMARY KEY,customer_id INT,full_name VARCHAR(100),phone VARCHAR(20),email VARCHAR(100))");
                    s.execute("CREATE TABLE guest_identity_vault(customer_id INT PRIMARY KEY,encrypted_id_number VARBINARY(512),encrypted_raw_payload BLOB,face_embedding_blob BLOB,captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    s.execute("INSERT INTO customers(id,full_name) VALUES(1,'OTHER'),(2,'OWN')");
                    s.execute("INSERT INTO users(id,customer_id) VALUES(1,2),(2,1)");
                }
                var vault=new GuestIdentityVaultDao(new AesGcmEncryptionUtil(new byte[32]),new byte[32]);
                var data=input();data.addProperty("customerId",1);data.addProperty("userId",2);
                var result=CustomerProfileService.save(c,1,CustomerProfileService.validate(data),vault);
                assertEquals(2,result.get("customerId").getAsInt());assertTrue(result.get("identitySaved").getAsBoolean());
                assertFalse(result.has("identityCard"));assertEquals("000000000000",vault.readIdentity(c,2));
                try(var s=c.createStatement();var r=s.executeQuery("SELECT full_name,identity_card FROM customers WHERE id=1")){r.next();assertEquals("OTHER",r.getString(1));assertNull(r.getString(2));}
                assertThrows(SQLException.class,()->CustomerProfileService.save(c,2,CustomerProfileService.validate(data),vault));
                try(var s=c.createStatement();var r=s.executeQuery("SELECT full_name FROM customers WHERE id=1")){r.next();assertEquals("OTHER",r.getString(1));}
                data.remove("identityCard");CustomerProfileService.save(c,1,CustomerProfileService.validate(data),vault);
                assertEquals("000000000000",vault.readIdentity(c,2),"Blank identity preserves the encrypted value");
            } finally {
                assertTrue(schema.matches("profile_[0-9a-f]{32}_test"));
                try(var cleanup=c.createStatement()){cleanup.execute("DROP DATABASE `"+schema+"`");}
            }
        }
    }
}
