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
            return new Result(409, "Chỉ đơn chờ xác nhận mới có thể được xác nhận");
        }
        if (booking.checkIn().before(Timestamp.from(Instant.now()))) {
            return new Result(409, "Không thể xác nhận đơn có ngày nhận phòng đã qua");
        }
        try (PreparedStatement conflict = connection.prepareStatement(
                "SELECT id FROM bookings WHERE room_id=? AND id<>? "
                + "AND status IN ('pending','confirmed','checked_in') "
                + "AND check_in_date < ? AND check_out_date > ? LIMIT 1 FOR UPDATE")) {
            conflict.setInt(1, booking.roomId());
            conflict.setInt(2, booking.id());
            conflict.setTimestamp(3, booking.checkOut());
            conflict.setTimestamp(4, booking.checkIn());
            try (ResultSet rows = conflict.executeQuery()) {
                if (rows.next()) return new Result(409, "Phòng đã có đơn lưu trú trùng thời gian");
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE bookings SET status='confirmed' WHERE id=? AND status='pending'")) {
            update.setInt(1, booking.id());
            if (update.executeUpdate() != 1) return new Result(409, "Đơn đặt phòng vừa được cập nhật, vui lòng tải lại");
        }
        return new Result(200, "Đã xác nhận đơn đặt phòng");
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
        try (PreparedStatement folio = connection.prepareStatement(
                "SELECT 1 FROM folios WHERE booking_id=? AND (status IN ('settled','closed') OR balance<>0) LIMIT 1")) {
            folio.setInt(1, bookingId);
            try (ResultSet rows = folio.executeQuery()) { return rows.next(); }
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
