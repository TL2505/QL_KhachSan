package quanlykhachsan.backend.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Produces idempotent DBR snapshots directly from posted folio ledger transactions. */
public final class DbrReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final DbrLedgerDao dao;
    public DbrReportService() { this(new DbrLedgerDao()); }
    public DbrReportService(DbrLedgerDao dao) { this.dao = Objects.requireNonNull(dao); }

    public DbrLedgerDao.DbrMetrics generate(LocalDate date) throws SQLException {
        Objects.requireNonNull(date, "date");
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                DbrLedgerDao.DbrInputs input = dao.aggregate(c, date);
                DbrLedgerDao.DbrMetrics metrics = calculate(input);
                dao.save(c, date, metrics);
                c.commit();
                return metrics;
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }
    }

    public static DbrLedgerDao.DbrMetrics calculate(DbrLedgerDao.DbrInputs input) {
        BigDecimal total = money(input.totalRevenue());
        BigDecimal room = money(input.roomRevenue());
        int occupied = Math.max(0, input.occupiedRooms());
        int available = Math.max(0, input.availableRooms());
        BigDecimal occupancy = available == 0 ? ZERO : BigDecimal.valueOf(occupied)
            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);
        BigDecimal adr = occupied == 0 ? ZERO : room.divide(BigDecimal.valueOf(occupied), 2, RoundingMode.HALF_UP);
        BigDecimal revpar = available == 0 ? ZERO : room.divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);
        return new DbrLedgerDao.DbrMetrics(total, room, occupied, available, occupancy, adr, revpar);
    }

    private static BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }
}
