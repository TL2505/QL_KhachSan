package quanlykhachsan.backend.customer;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuestBookingOwnershipTest {
    static final class Database {
        final String phone; final List<String> statements=new ArrayList<>();boolean committed,rolledBack;
        Database(String phone){this.phone=phone;}
        Connection open(){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("commit"))committed=true;
            if(m.getName().equals("rollback"))rolledBack=true;
            if(m.getName().equals("prepareStatement")){
                String sql=(String)a[0];statements.add(sql);
                assertTrue(sql.startsWith("SELECT "),"Owned capture without identity must not insert or update another profile");
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(q,method,args)->{
                    if(method.getName().equals("executeQuery")){int[] row={0};return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(r,get,values)->switch(get.getName()){
                        case "next"->++row[0]==1;case "getString"->phone;case "getInt"->1;case "close"->null;default->throw new AssertionError(get.getName());});}
                    return null;
                });
            }
            return null;
        });}
    }
    @Test void ownedCustomerIsLockedAndNeverReplacedByPhoneSearch()throws Exception {
        var db=new Database("+84901234567");
        assertEquals(37,new GuestIngestionService(db::open).ingestOwned(37,"Isolated Guest","0901234567",null,null));
        assertTrue(db.committed);assertTrue(db.statements.contains("SELECT phone FROM customers WHERE id=? FOR UPDATE"));
        assertTrue(db.statements.stream().noneMatch(sql->sql.contains("phone IN")));
    }
    @Test void phoneMismatchRollsBackAndDoesNotCreateAnyCustomer() {
        var db=new Database("0912345678");
        assertThrows(SecurityException.class,()->new GuestIngestionService(db::open).ingestOwned(37,"Isolated Guest","0901234567",null,null));
        assertFalse(db.committed);assertTrue(db.rolledBack);
    }
}
