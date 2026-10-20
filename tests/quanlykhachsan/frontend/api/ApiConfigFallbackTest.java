package quanlykhachsan.frontend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ApiConfigFallbackTest {
    private static final String VM_JDBC_URL =
        "jdbc:mysql://192.168.56.102:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC";

    @Test
    void fallsBackToLocalhostOnWindowsOnlyWhenConfiguredHostIsUnreachable() {
        String actual = ApiConfig.resolveWindowsDbFallback(
            VM_JDBC_URL,
            true,
            (host, port) -> "localhost".equals(host) && port == 3306);

        assertEquals(
            "jdbc:mysql://localhost:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC",
            actual);
    }

    @Test
    void keepsConfiguredHostWhenItIsReachable() {
        AtomicInteger probes = new AtomicInteger();

        String actual = ApiConfig.resolveWindowsDbFallback(
            VM_JDBC_URL,
            true,
            (host, port) -> {
                probes.incrementAndGet();
                return "192.168.56.102".equals(host) && port == 3306;
            });

        assertEquals(VM_JDBC_URL, actual);
        assertEquals(1, probes.get());
    }

    @Test
    void doesNotProbeOrFallbackOutsideWindows() {
        AtomicInteger probes = new AtomicInteger();

        String actual = ApiConfig.resolveWindowsDbFallback(
            VM_JDBC_URL,
            false,
            (host, port) -> {
                probes.incrementAndGet();
                return false;
            });

        assertEquals(VM_JDBC_URL, actual);
        assertEquals(0, probes.get());
    }

    @Test
    void retainsConfiguredHostWhenNeitherDatabaseEndpointIsReachable() {
        AtomicInteger probes = new AtomicInteger();

        String actual = ApiConfig.resolveWindowsDbFallback(
            VM_JDBC_URL,
            true,
            (host, port) -> {
                probes.incrementAndGet();
                return false;
            });

        assertEquals(VM_JDBC_URL, actual);
        assertEquals(2, probes.get());
    }
}
