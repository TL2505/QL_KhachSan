package quanlykhachsan.backend.booking;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookingFailureLogTest {
    @Test void includesSafeSqlDiagnosticsAndReferenceWithoutSensitiveExceptionMessages() {
        var sql = new SQLException("identity=PRIVATE_PASSPORT phone=PRIVATE_PHONE", "23000", 4025);
        sql.setNextException(new SQLException("password=PRIVATE_PASSWORD", "08001", 1045));
        var output = new ByteArrayOutputStream(); var previous = System.err;
        try {
            System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
            String reference = BookingFailureLog.record("public-booking", new RuntimeException("PRIVATE_CONTACT", sql));
            String logged = output.toString(StandardCharsets.UTF_8);
            assertTrue(logged.contains("reference=" + reference));
            assertTrue(logged.contains("sqlState=23000 vendorCode=4025"));
            assertTrue(logged.contains("sqlState=08001 vendorCode=1045"));
            assertTrue(logged.contains("operation=public-booking"));
            assertFalse(logged.contains("PRIVATE_"));
        } finally { System.setErr(previous); }
    }
}
