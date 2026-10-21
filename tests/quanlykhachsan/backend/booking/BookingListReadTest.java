package quanlykhachsan.backend.booking;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the JDBC contract, not a claim of testing the operator's live database. */
class BookingListReadTest {
    private Connection connection(boolean fail) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p,m,a) -> {
            if (m.getName().equals("prepareStatement")) {
                assertEquals("SELECT * FROM bookings ORDER BY created_at DESC, id DESC", a[0]);
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PreparedStatement.class}, (q,qm,qa) -> {
                    if (qm.getName().equals("executeQuery")) {
                        if (fail) throw new SQLException("Database unavailable", "08001");
                        int[] index = {-1}; int[] ids = {5,3,99};
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResultSet.class}, (r,rm,ra) -> switch(rm.getName()) {
                            case "next" -> ++index[0] < ids.length;
                            case "getInt" -> ra[0].equals("id") ? ids[index[0]] : 1;
                            case "getTimestamp" -> Timestamp.valueOf(index[0] < 2 ? "2026-10-21 09:30:00" : "2026-10-20 09:30:00");
                            case "getDouble" -> 100.0;
                            case "getString" -> "pending";
                            case "close" -> null;
                            default -> throw new AssertionError(rm.getName());
                        });
                    }
                    if (qm.getName().equals("close")) return null;
                    throw new AssertionError(qm.getName());
                });
            }
            // The method must not close or commit a caller-owned connection.
            throw new AssertionError(m.getName());
        });
    }
    @Test void usesStableDatabaseCreationOrderAndKeepsActualTimestamp() throws Exception {
        var rows = BookingDAOImpl.selectBooking(connection(false));
        assertEquals(List.of(5,3,99), rows.stream().map(Booking::getId).toList());
        assertEquals(Timestamp.valueOf("2026-10-21 09:30:00"), rows.get(0).getCreatedAt());
        assertEquals(rows.get(0).getCreatedAt(), rows.get(1).getCreatedAt());
        assertTrue(rows.get(0).getCreatedAt().after(rows.get(2).getCreatedAt()));
    }
    @Test void failedReadIsNotReportedAsEmptySuccessfulList() {
        assertThrows(SQLException.class, () -> BookingDAOImpl.selectBooking(connection(true)));
    }
}
