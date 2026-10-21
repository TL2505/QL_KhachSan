package quanlykhachsan.backend.customer;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDirectoryTest {
    @Test void directoryIncludesExistingContactAndIdentityFields() throws Exception {
        var values = new LinkedHashMap<String, Object>();
        values.put("id", 55); values.put("full_name", "Synthetic Customer");
        values.put("phone", "0900000000"); values.put("identity_card", "000000000001");
        values.put("email", "synthetic@example.invalid"); values.put("address", "Synthetic Address");
        values.put("loyalty_level", "Member"); values.put("created_at", "2026-10-20");
        var fixture = fixture(values);
        var rows = CustomerDAOImpl.searchCustomers(fixture.connection(), null);
        assertEquals(List.of(values), rows);
        assertTrue(fixture.sql().get().endsWith("ORDER BY id DESC LIMIT 50"));
        assertTrue(fixture.parameters().isEmpty());
    }

    @Test void keywordSearchReturnsContactFieldsAndPreservesBoundSearchParameters() throws Exception {
        var values = new HashMap<String, Object>();
        values.put("id", 3); values.put("full_name", "Synthetic Customer");
        values.put("email", "synthetic@example.invalid"); values.put("address", "Synthetic Address");
        var fixture = fixture(values);
        var row = CustomerDAOImpl.searchCustomers(fixture.connection(), "  Synthetic  ").get(0);
        assertEquals("synthetic@example.invalid", row.get("email"));
        assertEquals("Synthetic Address", row.get("address"));
        assertEquals(Map.of(1, "%Synthetic%", 2, "%Synthetic%", 3, "%Synthetic%"), fixture.parameters());
        assertTrue(fixture.sql().get().contains("WHERE full_name LIKE ? OR phone LIKE ? OR identity_card LIKE ?"));
    }

    @Test void missingContactFieldsRemainNull() throws Exception {
        var fixture = fixture(Map.of("id", 4, "full_name", "Synthetic Customer"));
        var row = CustomerDAOImpl.searchCustomers(fixture.connection(), "").get(0);
        assertTrue(row.containsKey("email")); assertNull(row.get("email"));
        assertTrue(row.containsKey("address")); assertNull(row.get("address"));
    }

    private record Fixture(Connection connection, AtomicReference<String> sql, Map<Integer, String> parameters) { }
    private static Fixture fixture(Map<String, Object> values) {
        var sql = new AtomicReference<String>();
        var parameters = new HashMap<Integer, String>();
        var cursor = new AtomicInteger();
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(CustomerDirectoryTest.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            if (method.getName().equals("next")) return cursor.getAndIncrement() == 0;
            if (method.getName().equals("getString") || method.getName().equals("getInt")) {
                String column = (String) args[0];
                String selected = sql.get().substring(0, sql.get().indexOf(" FROM "));
                if (!Arrays.asList(selected.substring(7).split(", ")).contains(column)) throw new SQLException("Column not selected: " + column);
                Object value = values.get(column);
                return method.getName().equals("getInt") ? (value == null ? 0 : ((Number) value).intValue()) : value;
            }
            return null;
        });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(CustomerDirectoryTest.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
            if (method.getName().equals("executeQuery")) return rows;
            if (method.getName().equals("setString")) parameters.put((Integer) args[0], (String) args[1]);
            return null;
        });
        Connection connection = (Connection) Proxy.newProxyInstance(CustomerDirectoryTest.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement")) { sql.set((String) args[0]); return statement; }
            return null;
        });
        return new Fixture(connection, sql, parameters);
    }
}
