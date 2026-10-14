package quanlykhachsan.backend.system;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.booking.FinancialCalculator;
import quanlykhachsan.backend.booking.NoShowEngine;
import quanlykhachsan.backend.booking.TaxConfig;
import quanlykhachsan.backend.report.DbrReportService;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Seven-step night audit with independently committed checkpoints and 50-room chunks. */
public final class NightAuditEngine {
    private static final int CHUNK_SIZE = 50;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final StepFailureInjector failureInjector;

    public NightAuditEngine() { this(step -> { }); }
    public NightAuditEngine(StepFailureInjector failureInjector) { this.failureInjector = failureInjector; }

    public void run(LocalDate auditDate) throws SQLException {
        if (auditDate == null) throw new IllegalArgumentException("auditDate is required");
        SystemStateHolder.setFrozen(true);
        NightAuditStep current = NightAuditStep.INITIATE_FREEZE;
        try {
            step(auditDate, current, () -> SystemStateHolder.setFrozen(true));
            current = NightAuditStep.ROLL_ROOM_RATES;
            step(auditDate, current, () -> rollRoomRates(auditDate));
            current = NightAuditStep.PROCESS_NO_SHOWS;
            step(auditDate, current, () -> processNoShows(auditDate));
            current = NightAuditStep.AUXILIARY_ROLLUP;
            step(auditDate, current, () -> reconcileAuxiliaryTransactions(auditDate));
            current = NightAuditStep.RECONCILE_LEDGER;
            step(auditDate, current, this::reconcileLedger);
            current = NightAuditStep.CREATE_DBR;
            step(auditDate, current, () -> new quanlykhachsan.backend.report.DbrLedgerAggregator().generate(auditDate));
            current = NightAuditStep.ADVANCE_DATE;
            step(auditDate, current, () -> advanceBusinessDate(auditDate));
            SystemStateHolder.setFrozen(false);
        } catch (Exception ex) {
            fail(auditDate, current, ex);
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("Night audit failed at " + current.name(), ex);
        }
    }

    private void step(LocalDate date, NightAuditStep step, SqlAction action) throws Exception {
        if (isComplete(date, step)) return;
        mark(date, step, "running", null, null, false);
        failureInjector.beforeStep(step);
        try {
            action.run();
            mark(date, step, "completed", null, java.time.LocalDateTime.now(BUSINESS_ZONE), false);
        } catch (Exception ex) {
            mark(date, step, "failed", ex.getClass().getSimpleName() + ": " + ex.getMessage(), null, false);
            throw ex;
        }
    }

    private void rollRoomRates(LocalDate date) throws SQLException {
        TaxConfig tax = TaxConfig.fromRuntimeConfiguration();
        int offset = progress(date, NightAuditStep.ROLL_ROOM_RATES);
        while (true) {
            try (Connection c = DatabaseConnection.getConnection()) {
                c.setAutoCommit(false);
                try {
                    List<RoomCharge> chunk = loadRoomChunk(c, offset);
                    if (chunk.isEmpty()) { c.commit(); return; }
                    for (RoomCharge charge : chunk) postRoomRate(c, date, charge, tax);
                    offset = chunk.get(chunk.size() - 1).bookingId;
                    setProgress(c, date, NightAuditStep.ROLL_ROOM_RATES, offset);
                    c.commit();
                } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
                finally { c.setAutoCommit(true); }
            }
        }
    }

    private static List<RoomCharge> loadRoomChunk(Connection c, int offset) throws SQLException {
        List<RoomCharge> list = new ArrayList<>();
        try (PreparedStatement q = c.prepareStatement("SELECT b.id booking_id,r.price FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.status='checked_in' AND r.status IN ('occupied_clean','occupied_dirty') AND NOT EXISTS (SELECT 1 FROM folios f JOIN folio_transactions t ON t.folio_id=f.id WHERE f.booking_id=b.id AND t.reference_id=CONCAT('BOOKING-ROOM-',b.id)) AND b.id>? ORDER BY b.id LIMIT ?")) {
            q.setInt(1, offset); q.setInt(2, CHUNK_SIZE);
            try (ResultSet r = q.executeQuery()) { while (r.next()) list.add(new RoomCharge(r.getInt(1), r.getBigDecimal(2))); }
        }
        return list;
    }

    private static void postRoomRate(Connection c, LocalDate date, RoomCharge room, TaxConfig tax) throws SQLException {
        long folio;
        try (PreparedStatement q = c.prepareStatement("SELECT id FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1 FOR UPDATE")) {
            q.setInt(1, room.bookingId); try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Master folio missing for checked-in booking " + room.bookingId);
                folio = r.getLong(1);
            }
        }
        String prefix = "AUDIT-" + date.toString().replace("-", "") + "-" + room.bookingId;
        if (existsReference(c, prefix + "-ROOM")) return;
        FinancialCalculator.Calculation bill = FinancialCalculator.calculate(room.rate, BigDecimal.ZERO, tax);
        addDebit(c, folio, "room_rate", "Night audit room charge", bill.getRoomFee(), date, prefix + "-ROOM");
        addDebit(c, folio, "service_charge", "Night audit service charge", bill.getServiceFee(), date, prefix + "-SVC");
        addDebit(c, folio, "vat", "Night audit VAT", bill.getVat(), date, prefix + "-VAT");
        try (PreparedStatement q = c.prepareStatement("UPDATE folios SET balance=balance+?,status='open' WHERE id=?")) {
            q.setBigDecimal(1, bill.getTotalAmount()); q.setLong(2, folio);
            if (q.executeUpdate() != 1) throw new SQLException("Unable to update master folio during audit");
        }
    }

    private void processNoShows(LocalDate date) throws SQLException {
        NoShowEngine engine = new NoShowEngine();
        int processed;
        int total = 0;
        do {
            processed = engine.processBatch(LocalDateTime.now(BUSINESS_ZONE), date);
            total += processed;
            markProgress(date, NightAuditStep.PROCESS_NO_SHOWS, total);
        } while (processed == 50);
    }

    private void reconcileAuxiliaryTransactions(LocalDate date) throws SQLException {
        int count;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM folio_transactions WHERE business_date=? AND charge_type NOT IN ('room_rate','service_charge','vat','penalty')")) {
            q.setDate(1, java.sql.Date.valueOf(date)); try (ResultSet r = q.executeQuery()) { r.next(); count = r.getInt(1); }
        }
        markProgress(date, NightAuditStep.AUXILIARY_ROLLUP, count);
    }

    private void reconcileLedger() throws SQLException {
        String sql = "SELECT f.id,f.balance,COALESCE(SUM(CASE WHEN t.charge_type='credit_adjustment' THEN 0.00 WHEN t.transaction_type='debit' THEN t.amount ELSE -t.amount END),0.00) ledger "
            + "FROM folios f LEFT JOIN folio_transactions t ON t.folio_id=f.id GROUP BY f.id,f.balance "
            + "HAVING ABS(ROUND(f.balance-ledger,2))>0.00";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(sql);
             ResultSet r = q.executeQuery()) {
            if (r.next()) throw new SQLException("Folio ledger does not reconcile for folio " + r.getInt("id"));
        }
    }

    private static void advanceBusinessDate(LocalDate auditDate) throws SQLException {
        LocalDate nextDate = auditDate.plusDays(1);
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("UPDATE hotel_business_state SET business_date=? WHERE id=1 AND business_date<=?")) {
            q.setDate(1, java.sql.Date.valueOf(nextDate)); q.setDate(2, java.sql.Date.valueOf(auditDate));
            int changed = q.executeUpdate();
            if (changed == 0) {
                try (PreparedStatement read = c.prepareStatement("SELECT business_date FROM hotel_business_state WHERE id=1");
                     ResultSet r = read.executeQuery()) {
                    if (!r.next() || !nextDate.equals(r.getDate(1).toLocalDate())) throw new SQLException("Business date is not at the expected audit boundary");
                }
            }
        }
    }

    private boolean isComplete(LocalDate date, NightAuditStep step) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement("SELECT status FROM audit_checkpoints WHERE audit_date=? AND step_number=?")) {
            q.setDate(1, java.sql.Date.valueOf(date)); q.setInt(2, step.number());
            try (ResultSet r = q.executeQuery()) { return r.next() && "completed".equals(r.getString(1)); }
        }
    }

    private int progress(LocalDate date, NightAuditStep step) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement("SELECT processed_count FROM audit_checkpoints WHERE audit_date=? AND step_number=?")) {
            q.setDate(1, java.sql.Date.valueOf(date)); q.setInt(2, step.number());
            try (ResultSet r = q.executeQuery()) { return r.next() ? r.getInt(1) : 0; }
        }
    }

    private void markProgress(LocalDate date, NightAuditStep step, int count) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try { setProgress(c, date, step, count); c.commit(); }
            catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }
    }

    private static void setProgress(Connection c, LocalDate date, NightAuditStep step, int count) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("UPDATE audit_checkpoints SET processed_count=?,status='running',error_message=NULL WHERE audit_date=? AND step_number=?")) {
            q.setInt(1, count); q.setDate(2, java.sql.Date.valueOf(date)); q.setInt(3, step.number()); q.executeUpdate();
        }
    }

    private void mark(LocalDate date, NightAuditStep step, String status, String error, LocalDateTime complete, boolean ignored) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "INSERT INTO audit_checkpoints(audit_date,step_number,step_name,status,error_message,started_at,completed_at) VALUES(?,?,?,?,?,NOW(),?) "
              + "ON DUPLICATE KEY UPDATE status=VALUES(status),error_message=VALUES(error_message),started_at=COALESCE(started_at,NOW()),completed_at=VALUES(completed_at)")) {
            q.setDate(1, java.sql.Date.valueOf(date)); q.setInt(2, step.number()); q.setString(3, step.name());
            q.setString(4, status); q.setString(5, error);
            if (complete == null) q.setNull(6, java.sql.Types.TIMESTAMP); else q.setTimestamp(6, java.sql.Timestamp.valueOf(complete));
            q.executeUpdate();
        }
    }

    private void fail(LocalDate date, NightAuditStep step, Exception cause) {
        SystemStateHolder.setFrozen(true);
        try { mark(date, step, "failed", cause.getClass().getSimpleName() + ": " + cause.getMessage(), null, false); }
        catch (SQLException checkpointError) { cause.addSuppressed(checkpointError); }
    }

    private static boolean existsReference(Connection c, String reference) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM folio_transactions WHERE reference_id=? LIMIT 1")) {
            q.setString(1, reference); try (ResultSet r = q.executeQuery()) { return r.next(); }
        }
    }
    private static void addDebit(Connection c, long folio, String type, String description, BigDecimal amount,
            LocalDate date, String reference) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) VALUES(?,'debit',?,?,?,?,?)")) {
            q.setLong(1, folio); q.setString(2, type); q.setString(3, description); q.setBigDecimal(4, amount);
            q.setDate(5, java.sql.Date.valueOf(date)); q.setString(6, reference); q.executeUpdate();
        }
    }

    @FunctionalInterface private interface SqlAction { void run() throws Exception; }
    @FunctionalInterface public interface StepFailureInjector { void beforeStep(NightAuditStep step) throws Exception; }
    private record RoomCharge(int bookingId, BigDecimal rate) { }
}
