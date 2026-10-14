package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Processes one atomic batch of up to 50 overdue confirmed bookings. */
public final class NoShowEngine {
    private static final int BATCH_SIZE = 50;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public int processBatch(LocalDateTime cutoff) throws SQLException {
        return processBatch(cutoff, LocalDate.now(BUSINESS_ZONE));
    }

    public int processBatch(LocalDateTime cutoff, LocalDate businessDate) throws SQLException {
        if (cutoff == null) throw new IllegalArgumentException("cutoff is required");
        if (businessDate == null) throw new IllegalArgumentException("businessDate is required");
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<OverdueBooking> bookings = loadOverdue(connection, cutoff);
                int processed = 0;
                for (OverdueBooking booking : bookings) {
                    int changed;
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE bookings SET status='no_show' WHERE id=? AND status='confirmed'")) {
                        update.setInt(1, booking.bookingId);
                        changed = update.executeUpdate();
                    }
                    if (changed == 0) continue;

                    try (PreparedStatement room = connection.prepareStatement(
                            "UPDATE rooms SET status='vacant_dirty', version=version+1 "
                          + "WHERE id=? AND status='reserved'")) {
                        room.setInt(1, booking.roomId);
                        if (room.executeUpdate() != 1) {
                            throw new SQLException("Reserved room changed while processing no-show booking "
                                + booking.bookingId);
                        }
                    }

                    long folioId = findMasterFolio(connection, booking.bookingId);
                    BigDecimal penalty = booking.roomRate.setScale(2, RoundingMode.HALF_UP);
                    try (PreparedStatement transaction = connection.prepareStatement(
                            "INSERT INTO folio_transactions "
                          + "(folio_id, transaction_type, charge_type, description, amount, business_date, reference_id) "
                          + "VALUES (?, 'debit', 'penalty', ?, ?, ?, ?)")) {
                        transaction.setLong(1, folioId);
                        transaction.setString(2, "One-night no-show penalty for booking " + booking.bookingId);
                        transaction.setBigDecimal(3, penalty);
                        transaction.setDate(4, java.sql.Date.valueOf(businessDate));
                        transaction.setString(5, "NO-SHOW-" + booking.bookingId);
                        transaction.executeUpdate();
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE folios SET balance=balance+?, status='open' WHERE id=?")) {
                        update.setBigDecimal(1, penalty);
                        update.setLong(2, folioId);
                        if (update.executeUpdate() != 1) throw new SQLException("Master folio disappeared");
                    }
                    processed++;
                }
                connection.commit();
                return processed;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static List<OverdueBooking> loadOverdue(Connection connection, LocalDateTime cutoff)
            throws SQLException {
        String sql = "SELECT b.id, b.room_id, r.price FROM bookings b "
                   + "JOIN rooms r ON r.id=b.room_id WHERE b.status='confirmed' "
                   + "AND b.check_in_date<=? ORDER BY b.check_in_date, b.id LIMIT ? FOR UPDATE";
        List<OverdueBooking> result = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setTimestamp(1, java.sql.Timestamp.valueOf(cutoff));
            query.setInt(2, BATCH_SIZE);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(new OverdueBooking(rows.getInt("id"), rows.getInt("room_id"),
                        rows.getBigDecimal("price")));
                }
            }
        }
        return result;
    }

    private static long findMasterFolio(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1 FOR UPDATE")) {
            query.setInt(1, bookingId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new SQLException("Master folio not found for booking " + bookingId);
                return rows.getLong(1);
            }
        }
    }

    private static final class OverdueBooking {
        final int bookingId;
        final int roomId;
        final BigDecimal roomRate;
        OverdueBooking(int bookingId, int roomId, BigDecimal roomRate) {
            this.bookingId = bookingId;
            this.roomId = roomId;
            this.roomRate = roomRate;
        }
    }
}
