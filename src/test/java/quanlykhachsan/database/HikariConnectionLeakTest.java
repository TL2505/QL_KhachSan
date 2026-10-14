package quanlykhachsan.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.BaseIntegrationTest;
import quanlykhachsan.backend.utils.DatabaseConnection;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise-Grade HikariCP Connection Pool & Leak Test Suite (TASK-D2.3).
 * <p>
 * Definition of Done (DoD) Criteria:
 * 1. Multi-threaded Burst: Concurrently dispatch 200 requests via ExecutorService (40 threads),
 *    saturating the pool up to maximumPoolSize = 20.
 * 2. Active connections strictly return to 0 post-test (verified via HikariPoolMXBean).
 * 3. Zero connection leak warnings logged under 5000ms threshold.
 * 4. Genuine Leak Test: Deliberately hold connection > leakDetectionThreshold (2000ms),
 *    verifying activeConnections == 1 and ProxyLeakTask warning, then resetting to 0 upon close().
 * 5. Verify production DatabaseConnection configuration governance parity (5000ms threshold).
 */
public class HikariConnectionLeakTest extends BaseIntegrationTest {

    private static final int CONSECUTIVE_REQUEST_COUNT = 200;
    private static final int CONCURRENT_WORKER_THREADS = 40; // 40 concurrent threads to saturate 20-connection pool
    private static final long LEAK_DETECTION_THRESHOLD_MS = 5000L;
    private static final int MAX_POOL_SIZE = 20;
    private static final int MIN_IDLE = 5;

    private static HikariDataSource testDataSource;
    private static HikariPoolMXBean poolMxBean;
    private static final AtomicInteger connectionCounter = new AtomicInteger(0);

    @BeforeAll
    public static void setUpLeakTestSuite() {
        System.out.println("==================================================================");
        System.out.println("🚀 [TASK-D2.3] Initializing Enterprise HikariCP Connection Leak Test Suite...");
        System.out.println("==================================================================");

        HikariConfig config = new HikariConfig();
        config.setPoolName("HotelPMS-LeakTestPool");
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(30000);
        config.setLeakDetectionThreshold(LEAK_DETECTION_THRESHOLD_MS); // 5000ms threshold per specification
        config.setInitializationFailTimeout(10000);

        // Bind instrumented mock connection provider with non-null, valid JDBC descriptors
        config.setDataSource(createInstrumentedMockDataSource());

        testDataSource = new HikariDataSource(config);
        poolMxBean = testDataSource.getHikariPoolMXBean();

        Assertions.assertNotNull(poolMxBean, "HikariPoolMXBean must be available on initialized DataSource");
        System.out.println("✔ Test Pool Initialized: PoolName=" + config.getPoolName()
                + ", MaxPoolSize=" + config.getMaximumPoolSize()
                + ", LeakDetectionThreshold=" + config.getLeakDetectionThreshold() + "ms");
    }

    @AfterAll
    public static void tearDownLeakTestSuite() {
        if (testDataSource != null && !testDataSource.isClosed()) {
            testDataSource.close();
            System.out.println("🛑 [TASK-D2.3] Test HikariDataSource closed.");
        }
    }

    @Test
    @DisplayName("DoD 1 & 2: 200-Request Multi-Threaded Burst (40 threads) saturates 20-connection pool; active connections return to 0")
    public void testHikariPool_200ConcurrentRequests_PoolSaturationAndZeroLeak() throws Exception {
        System.out.println("⚡ [BURST TEST] Dispatching 200 requests across 40 concurrent worker threads...");
        System.out.println("   • Target Pool: MaximumPoolSize = " + MAX_POOL_SIZE + ", Worker Threads = " + CONCURRENT_WORKER_THREADS);

        ExecutorService threadPool = Executors.newFixedThreadPool(CONCURRENT_WORKER_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch saturationLatch = new CountDownLatch(MAX_POOL_SIZE);
        CountDownLatch doneLatch = new CountDownLatch(CONSECUTIVE_REQUEST_COUNT);
        AtomicBoolean releaseSaturationHold = new AtomicBoolean(false);

        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalErrors = new AtomicInteger(0);
        AtomicInteger peakActiveObserved = new AtomicInteger(0);
        AtomicInteger peakWaitingObserved = new AtomicInteger(0);

        Random random = new Random(42);

        // Capture System.err to detect any unexpected HikariCP leak warnings during the 200 requests
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        long startTime = System.currentTimeMillis();

        try {
            for (int i = 0; i < CONSECUTIVE_REQUEST_COUNT; i++) {
                final int requestId = i + 1;
                final int opType = random.nextInt(5);

                threadPool.submit(() -> {
                    try {
                        startLatch.await(); // Simultaneous burst start

                        // Strict try-with-resources resource hygiene
                        try (Connection conn = testDataSource.getConnection()) {
                            Assertions.assertNotNull(conn, "Connection must not be null");
                            Assertions.assertFalse(conn.isClosed(), "Connection must be active");

                            // Coordinate pool saturation: the first 20 threads hold connections briefly to guarantee 20/20 saturation
                            if (saturationLatch.getCount() > 0) {
                                saturationLatch.countDown();
                                while (!releaseSaturationHold.get()) {
                                    Thread.sleep(10);
                                }
                            }

                            // Sample pool contention metrics
                            int currentActive = poolMxBean.getActiveConnections();
                            int currentWaiting = poolMxBean.getThreadsAwaitingConnection();

                            peakActiveObserved.accumulateAndGet(currentActive, Math::max);
                            peakWaitingObserved.accumulateAndGet(currentWaiting, Math::max);

                            // Execute randomized domain operations
                            switch (opType) {
                                case 0 -> executeRoomStatusCheck(conn, requestId);
                                case 1 -> executeBookingLookup(conn, requestId);
                                case 2 -> executeCustomerLoyaltyCheck(conn, requestId);
                                case 3 -> executeFolioBalanceAudit(conn, requestId);
                                case 4 -> executeOptimisticVersionVerification(conn, requestId);
                            }

                            // Brief simulated business logic computation (1-5ms, far below 5000ms threshold)
                            Thread.sleep(2);

                            totalSuccess.incrementAndGet();
                        }
                    } catch (Exception e) {
                        totalErrors.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release all 40 threads simultaneously
            startLatch.countDown();

            // Await guaranteed 100% pool saturation (20/20 connections checked out simultaneously)
            boolean saturated = saturationLatch.await(10, TimeUnit.SECONDS);
            Assertions.assertTrue(saturated, "Pool must achieve 20 concurrent connections leased simultaneously");

            int activeAtPeak = poolMxBean.getActiveConnections();
            int waitingAtPeak = poolMxBean.getThreadsAwaitingConnection();
            peakActiveObserved.accumulateAndGet(activeAtPeak, Math::max);
            peakWaitingObserved.accumulateAndGet(waitingAtPeak, Math::max);

            System.out.println("🔥 [POOL SATURATION VERIFIED] Active Connections = " + activeAtPeak
                    + " / " + MAX_POOL_SIZE + ", Threads Queued Awaiting Connection = " + waitingAtPeak);

            // Release saturation hold: remaining threads proceed through pool
            releaseSaturationHold.set(true);

            // Await completion of all 200 operations
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            Assertions.assertTrue(completed, "All 200 concurrent requests must complete within 30 seconds");

        } finally {
            System.setErr(originalErr);
            threadPool.shutdown();
        }

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("⏱ 200 Concurrent Operations completed in " + duration + " ms");
        System.out.println("📊 Concurrency & Saturation Metrics:");
        System.out.println("   • Total Dispatched        : " + CONSECUTIVE_REQUEST_COUNT);
        System.out.println("   • Successful Acquisitions : " + totalSuccess.get());
        System.out.println("   • Failed Acquisitions     : " + totalErrors.get());
        System.out.println("   • Peak Active Connections : " + peakActiveObserved.get() + " / " + MAX_POOL_SIZE);
        System.out.println("   • Peak Threads Awaiting   : " + peakWaitingObserved.get());

        // Assert all 200 operations succeeded
        Assertions.assertEquals(CONSECUTIVE_REQUEST_COUNT, totalSuccess.get(),
                "All 200 concurrent operations must succeed");
        Assertions.assertEquals(0, totalErrors.get(),
                "Zero errors permitted during pool acquisition");

        // Verify pool saturation reached the maximum pool size of 20
        Assertions.assertEquals(MAX_POOL_SIZE, peakActiveObserved.get(),
                "Pool must reach maximum saturation of exactly 20 connections");
        Assertions.assertTrue(peakWaitingObserved.get() > 0,
                "Under 40 concurrent threads, awaiting threads queue must be exercised (> 0)");

        // Brief cooldown for final thread cleanup
        Thread.sleep(100);

        // CRITICAL DoD INVARIANT 1: Active connections return to 0 post-test
        int finalActive = poolMxBean.getActiveConnections();
        int finalWaiting = poolMxBean.getThreadsAwaitingConnection();
        System.out.println("✔ Final Pool State: Active=" + finalActive + ", Waiting=" + finalWaiting);

        Assertions.assertEquals(0, finalActive,
                "DoD INVARIANT: Active connections must strictly return to 0 post-test");
        Assertions.assertEquals(0, finalWaiting,
                "DoD INVARIANT: Zero threads awaiting connection post-test");

        // CRITICAL DoD INVARIANT 2: Zero leak warnings logged under 5000ms threshold
        String capturedLog = errCapture.toString(StandardCharsets.UTF_8);
        Assertions.assertFalse(capturedLog.contains("Connection leak detection triggered"),
                "DoD INVARIANT: Zero leak warnings logged under 5000ms threshold. Log output: " + capturedLog);
        System.out.println("✔ Zero leak warnings logged during 200-request multi-threaded burst.");
    }

    @Test
    @DisplayName("DoD 3: Genuine Leak Test — Holding connection > leakDetectionThreshold triggers ProxyLeakTask warning")
    public void testHikariPool_DeliberateConnectionLeak_TriggersProxyLeakTask() throws Exception {
        System.out.println("🧪 [GENUINE LEAK TEST] Setting up dedicated pool with 2000ms leak detection threshold...");

        HikariConfig leakConfig = new HikariConfig();
        leakConfig.setPoolName("HotelPMS-DeliberateLeakPool");
        leakConfig.setMaximumPoolSize(5);
        leakConfig.setMinimumIdle(1);
        leakConfig.setLeakDetectionThreshold(2000L); // 2000ms is HikariCP's minimum allowed threshold
        leakConfig.setInitializationFailTimeout(5000);
        leakConfig.setDataSource(createInstrumentedMockDataSource());

        HikariDataSource leakDataSource = new HikariDataSource(leakConfig);
        HikariPoolMXBean leakMxBean = leakDataSource.getHikariPoolMXBean();

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        Connection deliberateLeakedConn = null;
        try {
            // 1. Borrow connection without closing
            deliberateLeakedConn = leakDataSource.getConnection();
            Assertions.assertNotNull(deliberateLeakedConn, "Leased connection must not be null");
            Assertions.assertEquals(1, leakMxBean.getActiveConnections(), "Active connections must be 1 while borrowed");

            System.out.println("   • Connection checked out without close(). Sleeping 2500ms (> 2000ms threshold)...");
            // 2. Pause thread beyond the leak detection threshold (2500ms > 2000ms)
            Thread.sleep(2500);

            // 3. Verify active connections is still 1 (unclosed)
            Assertions.assertEquals(1, leakMxBean.getActiveConnections(),
                    "Active connections must remain 1 while unclosed");

            // 4. Return the leaked connection to the running pool
            deliberateLeakedConn.close();
            System.out.println("   • Leaked connection manually closed.");

            // Brief sleep for pool housekeeping to update metrics
            Thread.sleep(100);

            // 5. CRITICAL ORDER: Verify active connections drops to 0 while pool is still alive and running
            int activeInRunningPool = leakMxBean.getActiveConnections();
            System.out.println("✔ Active connections in running pool post-close: " + activeInRunningPool);
            Assertions.assertEquals(0, activeInRunningPool,
                    "Active connections must return to 0 in running pool before pool shutdown");

        } finally {
            // Restore System.err and close DataSource
            System.setErr(originalErr);
            if (leakDataSource != null && !leakDataSource.isClosed()) {
                leakDataSource.close();
            }
        }

        // 6. Automated assertion: Verify HikariCP ProxyLeakTask warning log was captured
        String capturedLog = errCapture.toString(StandardCharsets.UTF_8);
        System.out.println("🔍 Captured Logger Output during deliberate leak:\n" + capturedLog.trim());

        Assertions.assertTrue(capturedLog.contains("Connection leak detection triggered"),
                "HikariCP ProxyLeakTask must log a leak detection warning: 'Connection leak detection triggered'");
        System.out.println("✔ ProxyLeakTask verification passed: leak warning logged and connection recovered to 0 in running pool.");
    }

    @Test
    @DisplayName("Governance Parity: Verify production DatabaseConnection singleton pool configuration (5000ms threshold)")
    public void testDatabaseConnection_GovernanceConfigurationParity() {
        System.out.println("🔍 Inspecting production DatabaseConnection singleton pool configuration...");

        HikariDataSource prodDataSource = DatabaseConnection.getInstance().getDataSource();
        Assertions.assertNotNull(prodDataSource, "DatabaseConnection DataSource must not be null");

        // Verify strict governance configuration per TASK-A1.1 & TASK-D2.3
        Assertions.assertEquals(5000L, prodDataSource.getLeakDetectionThreshold(),
                "GOVERNANCE DoD: leakDetectionThreshold must be strictly 5000ms (5 seconds)");
        Assertions.assertEquals(20, prodDataSource.getMaximumPoolSize(),
                "GOVERNANCE: maximumPoolSize must be 20");
        Assertions.assertEquals(5, prodDataSource.getMinimumIdle(),
                "GOVERNANCE: minimumIdle must be 5");
        Assertions.assertEquals(300000L, prodDataSource.getIdleTimeout(),
                "GOVERNANCE: idleTimeout must be 300000ms (5 minutes)");
        Assertions.assertEquals(30000L, prodDataSource.getConnectionTimeout(),
                "GOVERNANCE: connectionTimeout must be 30000ms (30 seconds)");
        Assertions.assertEquals("HotelPMS-HikariPool", prodDataSource.getPoolName(),
                "GOVERNANCE: poolName must be 'HotelPMS-HikariPool'");

        System.out.println("✔ Production DatabaseConnection governance verified: leakDetectionThreshold = 5000ms, maxPoolSize = 20");
    }

    @Test
    @DisplayName("MXBean Accuracy: Verify HikariPoolMXBean accurately tracks connection lease and release")
    public void testHikariPoolMXBean_AccuratelyTracksLeaseAndReleaseLifecycle() throws Exception {
        System.out.println("🔍 Verifying MXBean real-time active connection tracking...");

        int initialActive = poolMxBean.getActiveConnections();
        Assertions.assertEquals(0, initialActive, "Initial active connections must be 0");

        Connection conn1 = testDataSource.getConnection();
        Assertions.assertEquals(1, poolMxBean.getActiveConnections(), "Active connections must increment to 1");

        Connection conn2 = testDataSource.getConnection();
        Assertions.assertEquals(2, poolMxBean.getActiveConnections(), "Active connections must increment to 2");

        conn2.close();
        Assertions.assertEquals(1, poolMxBean.getActiveConnections(), "Active connections must decrement to 1");

        conn1.close();
        Assertions.assertEquals(0, poolMxBean.getActiveConnections(), "Active connections must return to 0");

        System.out.println("✔ HikariPoolMXBean correctly tracked lifecycle: 0 -> 1 -> 2 -> 1 -> 0");
    }

    // =========================================================================
    // DOMAIN QUERY SIMULATION METHODS
    // =========================================================================

    private void executeRoomStatusCheck(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, room_number, status, version FROM rooms WHERE id = ?")) {
            ps.setInt(1, (id % 6) + 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rs.getInt("id");
                }
            }
        }
    }

    private void executeBookingLookup(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, room_id, status, total_price FROM bookings WHERE id = ?")) {
            ps.setInt(1, (id % 3) + 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rs.getString("status");
                }
            }
        }
    }

    private void executeCustomerLoyaltyCheck(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, full_name, loyalty_points FROM customers WHERE id = ?")) {
            ps.setInt(1, (id % 3) + 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rs.getInt("loyalty_points");
                }
            }
        }
    }

    private void executeFolioBalanceAudit(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT folio_id, balance, status FROM folios WHERE folio_id = ?")) {
            ps.setInt(1, (id % 2) + 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rs.getDouble("balance");
                }
            }
        }
    }

    private void executeOptimisticVersionVerification(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT version FROM rooms WHERE id = ? AND version >= 1")) {
            ps.setInt(1, (id % 6) + 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rs.getInt("version");
                }
            }
        }
    }

    // =========================================================================
    // INSTRUMENTED MOCK DATA SOURCE & CONNECTION PROXY
    // =========================================================================

    private static DataSource createInstrumentedMockDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                HikariConnectionLeakTest.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("getConnection".equals(name)) {
                            return createMockConnection(connectionCounter.incrementAndGet());
                        }
                        if ("unwrap".equals(name)) {
                            return proxy;
                        }
                        if ("isWrapperFor".equals(name)) {
                            return false;
                        }
                        if ("getLoginTimeout".equals(name)) {
                            return 30;
                        }
                        return null;
                    }
                }
        );
    }

    private static Connection createMockConnection(int connId) {
        AtomicBoolean isClosed = new AtomicBoolean(false);

        return (Connection) Proxy.newProxyInstance(
                HikariConnectionLeakTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("toString".equals(name)) {
                            return "MockConnection[id=" + connId + ", open=" + (!isClosed.get()) + "]";
                        }
                        if ("close".equals(name)) {
                            isClosed.set(true);
                            return null;
                        }
                        if ("isClosed".equals(name)) {
                            return isClosed.get();
                        }
                        if ("isValid".equals(name)) {
                            return !isClosed.get();
                        }
                        if ("getAutoCommit".equals(name)) {
                            return true;
                        }
                        if ("setAutoCommit".equals(name)) {
                            return null;
                        }
                        if ("clearWarnings".equals(name)) {
                            return null;
                        }
                        if ("getNetworkTimeout".equals(name)) {
                            return 30000;
                        }
                        if ("prepareStatement".equals(name)) {
                            return createMockPreparedStatement();
                        }
                        if ("createStatement".equals(name)) {
                            return createMockStatement();
                        }
                        if ("getMetaData".equals(name)) {
                            return createMockMetaData();
                        }
                        if ("unwrap".equals(name)) {
                            return proxy;
                        }
                        if ("isWrapperFor".equals(name)) {
                            return false;
                        }
                        Class<?> returnType = method.getReturnType();
                        if (returnType.equals(boolean.class)) return false;
                        if (returnType.equals(int.class)) return 0;
                        if (returnType.equals(long.class)) return 0L;
                        return null;
                    }
                }
        );
    }

    private static PreparedStatement createMockPreparedStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
                HikariConnectionLeakTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        return createMockResultSet();
                    }
                    if ("executeUpdate".equals(name)) {
                        return 1;
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) return false;
                    if (returnType.equals(int.class)) return 0;
                    return null;
                }
        );
    }

    private static Statement createMockStatement() {
        return (Statement) Proxy.newProxyInstance(
                HikariConnectionLeakTest.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        return createMockResultSet();
                    }
                    if ("executeUpdate".equals(name)) {
                        return 1;
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );
    }

    private static ResultSet createMockResultSet() {
        AtomicBoolean hasNext = new AtomicBoolean(true);
        return (ResultSet) Proxy.newProxyInstance(
                HikariConnectionLeakTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        return hasNext.getAndSet(false);
                    }
                    if ("getInt".equals(name)) {
                        return 1;
                    }
                    if ("getString".equals(name)) {
                        return "confirmed";
                    }
                    if ("getDouble".equals(name)) {
                        return 500000.0;
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );
    }

    private static DatabaseMetaData createMockMetaData() {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                HikariConnectionLeakTest.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                (proxy, method, args) -> {
                    if ("getDatabaseProductName".equals(method.getName())) {
                        return "MySQL";
                    }
                    if ("getDriverName".equals(method.getName())) {
                        return "HikariMockDriver";
                    }
                    return null;
                }
        );
    }
}
