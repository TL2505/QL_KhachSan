package quanlykhachsan.backend.booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * Administrative decisions for a booking.  This deliberately handles only
 * confirmation and cancellation: check-in, checkout and housekeeping remain
 * reception workflows and must use their dedicated operational endpoints.
 */
final class AdminBookingStatusService {
    record Result(int httpStatus, String message) { }

    Result decide(int bookingId, String requestedStatus) throws SQLException {
        String target = requestedStatus == null ? "" : requestedStatus.trim().toLowerCase(Locale.ROOT);
        if (!target.equals("confirmed") && !target.equals("cancelled")) {
            return new Result(400, "Trạng thái yêu cầu không hợp lệ");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BookingRow booking = lockBooking(connection, bookingId);
                if (booking == null) {
                    connection.rollback();
                    return new Result(404, "Không tìm thấy đơn đặt phòng");
                }

                Result result = target.equals("confirmed")
                        ? confirm(connection, booking)
                        : cancel(connection, booking);
                if (result.httpStatus() != 200) {
                    connection.rollback();
                    return result;
                }
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                if (error instanceof SQLException sql) throw sql;
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Reception may make the same reservation decisions, but never check out directly. */
    Result decideForReception(int bookingId, String requestedStatus) throws SQLException {
        return decide(bookingId, requestedStatus);
    }

    Result checkIn(int bookingId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BookingRow booking = lockBooking(connection, bookingId);
                if (booking == null) { connection.rollback(); return new Result(404, "Không tìm thấy đơn đặt phòng"); }
                if (!"confirmed".equals(booking.status())) { connection.rollback(); return new Result(409, "Chỉ đơn đã xác nhận mới được nhận phòng"); }
                java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
                if (today.isBefore(booking.checkIn().toLocalDateTime().toLocalDate()) || !today.isBefore(booking.checkOut().toLocalDateTime().toLocalDate())) {
                    connection.rollback(); return new Result(409, "Chưa đến ngày nhận phòng hoặc đơn đã quá ngày lưu trú");
                }
                try (PreparedStatement updateBooking = connection.prepareStatement("UPDATE bookings SET status='checked_in' WHERE id=? AND status='confirmed'")) {
                    updateBooking.setInt(1, booking.id());
                    if (updateBooking.executeUpdate() != 1) { connection.rollback(); return new Result(409, "Đơn đặt phòng vừa được cập nhật, vui lòng tải lại"); }
                }
                try (PreparedStatement updateRoom = connection.prepareStatement("UPDATE rooms SET status='occupied_clean',version=version+1 WHERE id=? AND status IN ('reserved','vacant_clean')")) {
                    updateRoom.setInt(1, booking.roomId());
                    if (updateRoom.executeUpdate() != 1) { connection.rollback(); return new Result(409, "Trạng thái phòng không phù hợp để nhận phòng"); }
                }
                connection.commit();
                return new Result(200, "Nhận phòng thành công");
            } catch (Exception error) {
                connection.rollback();
                if (error instanceof SQLException sql) throw sql;
                throw new SQLException("Không thể nhận phòng", error);
            } finally { connection.setAutoCommit(true); }
        }
    }

    private static Result confirm(Connection connection, BookingRow booking) throws SQLException {
        if (!"pending".equals(booking.status())) {
            return new Result(409, "Only pending bookings can be confirmed");
        }
        if (!booking.checkIn().after(Timestamp.from(Instant.now()))) {
            return new Result(409, "The check-in time has passed; handle this booking at reception");
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE bookings SET status='confirmed' WHERE id=? AND status='pending'")) {
            update.setInt(1, booking.id());
            if (update.executeUpdate() != 1) {
                return new Result(409, "Booking was updated; please refresh and try again");
            }
        }
        return new Result(200, "Booking confirmed");
    }

    private static Result cancel(Connection connection, BookingRow booking) throws SQLException {
        if (!"pending".equals(booking.status()) && !"confirmed".equals(booking.status())) {
            return new Result(409, "Chỉ đơn chờ xác nhận hoặc đã xác nhận mới có thể hủy tại Admin");
        }
        if (!booking.checkIn().after(Timestamp.from(Instant.now()))) {
            return new Result(409, "Không thể hủy đơn đã đến ngày nhận phòng; hãy xử lý tại Lễ tân");
        }
        if (hasFinancialActivity(connection, booking.id())) {
            return new Result(409, "Không thể hủy đơn đã có thanh toán hoặc hóa đơn");
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE bookings SET status='cancelled' WHERE id=? AND status IN ('pending','confirmed')")) {
            update.setInt(1, booking.id());
            if (update.executeUpdate() != 1) return new Result(409, "Đơn đặt phòng vừa được cập nhật, vui lòng tải lại");
        }
        voidUnpaidFolioBalances(connection, booking.id());
        // Legacy reservations reserve the room immediately.  Release only that
        // exact operational state; maintenance and other staff states are kept.
        try (PreparedStatement release = connection.prepareStatement(
                "UPDATE rooms SET status='vacant_clean', version=version+1 WHERE id=? AND status='reserved'")) {
            release.setInt(1, booking.roomId());
            release.executeUpdate();
        }
        return new Result(200, "Đã hủy đơn đặt phòng");
    }

    private static boolean hasFinancialActivity(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement invoice = connection.prepareStatement("SELECT 1 FROM invoices WHERE booking_id=? LIMIT 1")) {
            invoice.setInt(1, bookingId);
            try (ResultSet rows = invoice.executeQuery()) { if (rows.next()) return true; }
        }
        try (PreparedStatement payment = connection.prepareStatement(
                "SELECT 1 FROM folio_transactions t JOIN folios f ON f.id=t.folio_id "
              + "WHERE f.booking_id=? AND t.transaction_type='credit' AND t.charge_type='payment' LIMIT 1")) {
            payment.setInt(1, bookingId);
            try (ResultSet rows = payment.executeQuery()) { return rows.next(); }
        }
    }

    /** Booking charges are expected on a new reservation, not evidence of payment. */
    private static void voidUnpaidFolioBalances(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id,balance FROM folios WHERE booking_id=? FOR UPDATE")) {
            query.setInt(1, bookingId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    long folioId = rows.getLong("id");
                    java.math.BigDecimal balance = rows.getBigDecimal("balance");
                    if (balance.signum() > 0) {
                        try (PreparedStatement reversal = connection.prepareStatement(
                                "INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) "
                              + "SELECT ?,'credit','credit_adjustment','Cancelled unpaid booking',?,business_date,? FROM hotel_business_state WHERE id=1")) {
                            reversal.setLong(1, folioId);
                            reversal.setBigDecimal(2, balance);
                            reversal.setString(3, "CANCELLED-BOOKING-" + bookingId + "-FOLIO-" + folioId);
                            if (reversal.executeUpdate() != 1) throw new SQLException("Hotel business date missing");
                        }
                    }
                }
            }
        }
        try (PreparedStatement closeFolios = connection.prepareStatement(
                "UPDATE folios SET balance=0,status='closed' WHERE booking_id=?")) {
            closeFolios.setInt(1, bookingId);
            closeFolios.executeUpdate();
        }
    }

    private static BookingRow lockBooking(Connection connection, int id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id,room_id,check_in_date,check_out_date,status FROM bookings WHERE id=? FOR UPDATE")) {
            query.setInt(1, id);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? new BookingRow(rows.getInt(1), rows.getInt(2), rows.getTimestamp(3), rows.getTimestamp(4), rows.getString(5).toLowerCase(Locale.ROOT)) : null;
            }
        }
    }

    private record BookingRow(int id, int roomId, Timestamp checkIn, Timestamp checkOut, String status) { }
}
