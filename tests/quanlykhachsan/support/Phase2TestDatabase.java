package quanlykhachsan.support;

import java.sql.*;
import org.junit.jupiter.api.Assumptions;
import quanlykhachsan.backend.utils.*;

/** Tests write only to an explicitly selected disposable *_test database. */
public final class Phase2TestDatabase {
    private Phase2TestDatabase() { }
    public static void require() throws Exception {
        if(System.getenv("JWT_SECRET")==null)System.setProperty("hotel.jwt.secret","synthetic-phase2-jwt-test-fixture-secret-only");
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("HOTEL_RUN_DB_INTEGRATION"))
            && System.getenv("DB_NAME") != null && System.getenv("DB_NAME").endsWith("_test"), "Requires disposable MariaDB 11 test schema");
        try (Connection c=DatabaseConnection.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT VERSION()")) {
            r.next(); if (!r.getString(1).startsWith("11.") || !r.getString(1).contains("MariaDB")) throw new IllegalStateException("MariaDB 11 required");
            UpdateDbUtility.migrate(c);
        }
    }
    public static long scalar(String sql) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }
}
