package quanlykhachsan.backend.system;


import quanlykhachsan.backend.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Global Hotel System State Holder (TASK-A3.1 / TASK-B3.1).
 * <p>
 * Manages in-memory system freeze flags and the active operational business date
 * for Night Audit transitions and offline synchronization:
 * <ul>
 *   <li><b>System Freeze:</b> When true (00:00 - 00:05), mutating requests return 503 Retry-After: 300 in &lt; 1ms.</li>
 *   <li><b>Business Date:</b> Tracks current hotel operating date, advancing upon Night Audit Step 7 completion.</li>
 *   <li><b>Persistence:</b> Restores active business date from {@code system_settings} table upon restart.</li>
 * </ul>
 */
public class SystemStateHolder {

    public static final LocalDate DEFAULT_INITIAL_DATE = LocalDate.now();

    private static volatile boolean systemFrozen = false;
    private static volatile LocalDate currentBusinessDate = DEFAULT_INITIAL_DATE;

    private SystemStateHolder() {
        // Prevent instantiation
    }

    /**
     * Checks if the system is currently frozen during Night Audit.
     */
    public static boolean isSystemFrozen() {
        return systemFrozen;
    }

    /**
     * Sets the system freeze state.
     */
    public static void setSystemFrozen(boolean frozen) {
        systemFrozen = frozen;
    }

    /**
     * Backward-compatible alias for setSystemFrozen.
     */
    public static void setFrozen(boolean frozen) {
        setSystemFrozen(frozen);
    }

    /**
     * Returns current operational business date.
     */
    public static LocalDate getCurrentBusinessDate() {
        return currentBusinessDate != null ? currentBusinessDate : DEFAULT_INITIAL_DATE;
    }

    /**
     * Backward-compatible alias for getCurrentBusinessDate.
     */
    public static LocalDate getBusinessDate() {
        return getCurrentBusinessDate();
    }

    /**
     * Explicitly sets the operational business date and synchronously persists it to system_settings.
     */
    public static void setBusinessDate(LocalDate date) {
        currentBusinessDate = (date != null) ? date : DEFAULT_INITIAL_DATE;
        persistBusinessDateDirectly(currentBusinessDate);
    }

    /**
     * Advances the operational hotel business date by 1 day (Night Audit Step 7) and synchronously persists to DB.
     * Prevents accounting date rollback upon crash or container restart.
     */
    public static void rollNextBusinessDate() {
        if (currentBusinessDate != null) {
            currentBusinessDate = currentBusinessDate.plusDays(1);
        } else {
            currentBusinessDate = DEFAULT_INITIAL_DATE.plusDays(1);
        }
        persistBusinessDateDirectly(currentBusinessDate);
    }

    /**
     * Backward-compatible alias for rollNextBusinessDate.
     */
    public static void advanceBusinessDate() {
        rollNextBusinessDate();
    }

    /**
     * Loads the active business date from `system_settings` table upon server startup.
     */
    public static synchronized void loadFromDatabase() {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            ensureSettingsTableExists(conn);
            String querySql = "SELECT setting_value FROM system_settings WHERE setting_key = 'business_date'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(querySql)) {
                if (rs.next()) {
                    String val = rs.getString("setting_value");
                    if (val != null && !val.isBlank()) {
                        currentBusinessDate = LocalDate.parse(val.trim());
                    }
                } else {
                    persistBusinessDateSync(conn, getCurrentBusinessDate());
                }
            }
        } catch (Exception ignored) {
            // Graceful fallback to default in-memory date if table does not exist yet
        }
    }

    /**
     * Synchronously persists business date using an existing database connection.
     */
    public static void persistBusinessDateSync(Connection conn, LocalDate date) throws SQLException {
        if (conn == null || conn.isClosed() || date == null) return;
        ensureSettingsTableExists(conn);
        String upsertSql = "INSERT INTO system_settings (setting_key, setting_value) VALUES ('business_date', ?) " +
                           "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = NOW()";
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, date.toString());
            ps.executeUpdate();
        }
    }

    /**
     * Synchronously persists the business date to database to eliminate daemon-thread race conditions.
     */
    public static void persistBusinessDateDirectly(LocalDate date) {
        if (!DatabaseConnection.isDatabaseAvailable() || date == null) {
            return;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            persistBusinessDateSync(conn, date);
        } catch (Exception ignored) {
        }
    }

    /**
     * Backward-compatible delegate for asynchronous date persistence.
     */
    @Deprecated
    public static void persistBusinessDateAsync(LocalDate date) {
        persistBusinessDateDirectly(date);
    }

    /**
     * Ensures `system_settings` table exists in database.
     */
    private static void ensureSettingsTableExists(Connection conn) throws SQLException {
        String createTableSql = "CREATE TABLE IF NOT EXISTS system_settings (" +
                                "setting_key VARCHAR(100) PRIMARY KEY, " +
                                "setting_value VARCHAR(255) NOT NULL, " +
                                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                                ") ENGINE=InnoDB";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSql);
        }
    }

    /**
     * Resets system state back to default baseline.
     */
    public static void resetToDefault() {
        systemFrozen = false;
        currentBusinessDate = DEFAULT_INITIAL_DATE;
    }

    /**
     * Backward-compatible alias for resetToDefault.
     */
    public static void reset() {
        resetToDefault();
    }

}
