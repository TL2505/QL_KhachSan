package quanlykhachsan.backend.report;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** Ledger reads and idempotent persistence for a nightly business report. */
public final class DbrLedgerDao {
    public DbrInputs aggregate(Connection c, LocalDate date) throws SQLException {
        String sql = "SELECT COALESCE(SUM(CASE WHEN transaction_type='debit' THEN amount WHEN transaction_type='credit' AND charge_type IN ('refund','adjustment') THEN -amount ELSE 0 END),0.00), COALESCE(SUM(CASE WHEN charge_type='room_rate' THEN CASE WHEN transaction_type='debit' THEN amount ELSE -amount END ELSE 0 END),0.00) FROM folio_transactions WHERE business_date=?";
        BigDecimal total; BigDecimal room;
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet r = q.executeQuery()) { r.next(); total = r.getBigDecimal(1); room = r.getBigDecimal(2); }
        }
        int occupied; int available;
        try (PreparedStatement q = c.prepareStatement("SELECT SUM(status IN ('occupied_clean','occupied_dirty')), SUM(status NOT IN ('out_of_order','out_of_service')) FROM rooms"); ResultSet r = q.executeQuery()) {
            r.next(); occupied = r.getInt(1); available = r.getInt(2);
        }
        return new DbrInputs(total, room, occupied, available);
    }

    public void save(Connection c, LocalDate date, DbrMetrics metrics) throws SQLException {
        String sql = "INSERT INTO daily_business_reports(audit_date,total_revenue,room_revenue,occupied_rooms,available_rooms,occupancy_rate,adr,revpar) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE total_revenue=VALUES(total_revenue),room_revenue=VALUES(room_revenue),occupied_rooms=VALUES(occupied_rooms),available_rooms=VALUES(available_rooms),occupancy_rate=VALUES(occupancy_rate),adr=VALUES(adr),revpar=VALUES(revpar)";
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setDate(1, java.sql.Date.valueOf(date)); q.setBigDecimal(2, metrics.totalRevenue()); q.setBigDecimal(3, metrics.roomRevenue());
            q.setInt(4, metrics.occupiedRooms()); q.setInt(5, metrics.availableRooms()); q.setBigDecimal(6, metrics.occupancyRate());
            q.setBigDecimal(7, metrics.adr()); q.setBigDecimal(8, metrics.revpar()); q.executeUpdate();
        }
    }
    public record DbrInputs(BigDecimal totalRevenue, BigDecimal roomRevenue, int occupiedRooms, int availableRooms) { }
    public record DbrMetrics(BigDecimal totalRevenue, BigDecimal roomRevenue, int occupiedRooms, int availableRooms,
            BigDecimal occupancyRate, BigDecimal adr, BigDecimal revpar) { }
}
