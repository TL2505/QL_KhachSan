package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import quanlykhachsan.backend.pricing.DynamicPricingEngine;
import quanlykhachsan.backend.room.RentalType;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.PortalValidation.DateRange;

/** Posts the incremental room, service and tax charges with the stay extension. */
public final class StayExtensionService {
    private static final BigDecimal MAX_DECIMAL_12_2 = new BigDecimal("9999999999.99");
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    public Result extend(int bookingId, LocalDateTime proposedCheckOut) throws SQLException {
        if (bookingId < 1 || proposedCheckOut == null) throw new IllegalArgumentException("Invalid stay extension");
        if (!LocalTime.NOON.equals(proposedCheckOut.toLocalTime())) {
            throw new IllegalArgumentException("Daily stay extensions must use the 12:00 check-out time");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                int roomId = findRoomId(connection, bookingId);
                lockRoom(connection, roomId);

                BookingState booking = lockBooking(connection, bookingId);
                if (booking.roomId() != roomId) throw new IllegalStateException("Booking room changed; reload and retry");
                if (!"checked_in".equalsIgnoreCase(booking.status())) {
                    throw new IllegalStateException("Chỉ có thể gia hạn đơn đang lưu trú.");
                }
                if (proposedCheckOut.isEqual(booking.checkOut())) {
                    BigDecimal balance = masterFolioBalance(connection, bookingId);
                    connection.commit();
                    return new Result(booking.checkOut(), BigDecimal.ZERO.setScale(2), balance, true);
                }
                if (!proposedCheckOut.isAfter(booking.checkOut())) {
                    throw new IllegalArgumentException("Ngày trả phòng mới phải muộn hơn ngày hiện tại.");
                }
                if (RentalType.parse(booking.rentalType()) != RentalType.DAILY) {
                    throw new IllegalStateException("Chỉ hỗ trợ gia hạn lưu trú theo ngày; đơn theo giờ/qua đêm cần lễ tân xử lý theo gói giá.");
                }
                if (hasRedeemedPromotion(connection, bookingId)) {
                    throw new IllegalStateException("Booking đã dùng voucher; cần lễ tân báo giá lại trước khi gia hạn.");
                }

                StayExtensionGuard.validateStayExtension(bookingId, roomId, proposedCheckOut);
                var quote = new DynamicPricingEngine().quote(connection, booking.roomTypeId(),
                        new DateRange(booking.checkOut().toLocalDate(), proposedCheckOut.toLocalDate()));
                BigDecimal additional = quote.total_amount().setScale(2, java.math.RoundingMode.HALF_UP);
                if (additional.signum() <= 0) throw new IllegalStateException("Không thể tạo báo giá dương cho thời gian gia hạn.");
                if (booking.totalPrice().add(additional).compareTo(MAX_DECIMAL_12_2) > 0) {
                    throw new IllegalArgumentException("Tổng tiền booking vượt giới hạn lưu trữ.");
                }

                FolioState folio = lockMasterFolio(connection, bookingId);
                BigDecimal newBalance = folio.balance().add(additional).setScale(2, java.math.RoundingMode.HALF_UP);
                if (newBalance.compareTo(MAX_DECIMAL_12_2) > 0) throw new IllegalArgumentException("Số dư folio vượt giới hạn lưu trữ.");

                String extensionKey = "STAY-EXT-B" + bookingId + "-" + proposedCheckOut.toLocalDate().format(DATE_KEY);
                postCharge(connection, folio.id(), "room_rate", "Phòng nghỉ gia hạn", quote.room_subtotal(), extensionKey + "-ROOM");
                postCharge(connection, folio.id(), "service_charge", "Phí dịch vụ gia hạn", quote.service_charge(), extensionKey + "-SERVICE");
                postCharge(connection, folio.id(), "vat", "VAT thời gian gia hạn", quote.vat(), extensionKey + "-VAT");

                try (PreparedStatement updateFolio = connection.prepareStatement(
                        "UPDATE folios SET balance=?,status='open' WHERE id=? AND folio_type='master'")) {
                    updateFolio.setBigDecimal(1, newBalance);
                    updateFolio.setLong(2, folio.id());
                    if (updateFolio.executeUpdate() != 1) throw new SQLException("Master folio update was not confirmed");
                }
                try (PreparedStatement updateBooking = connection.prepareStatement(
                        "UPDATE bookings SET check_out_date=?,room_release_at=TIMESTAMPADD(MINUTE,buffer_minutes,?),total_price=total_price+? WHERE id=? AND status='checked_in' AND check_out_date=?")) {
                    updateBooking.setTimestamp(1, Timestamp.valueOf(proposedCheckOut));
                    updateBooking.setTimestamp(2, Timestamp.valueOf(proposedCheckOut));
                    updateBooking.setBigDecimal(3, additional);
                    updateBooking.setInt(4, bookingId);
                    updateBooking.setTimestamp(5, Timestamp.valueOf(booking.checkOut()));
                    if (updateBooking.executeUpdate() != 1) throw new IllegalStateException("Booking state changed; reload and retry");
                }

                connection.commit();
                return new Result(proposedCheckOut, additional, newBalance, false);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static int findRoomId(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT room_id FROM bookings WHERE id=?")) {
            query.setInt(1, bookingId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("Booking not found");
                return row.getInt(1);
            }
        }
    }

    private static void lockRoom(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM rooms WHERE id=? FOR UPDATE")) {
            query.setInt(1, roomId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("Booking room is unavailable");
            }
        }
    }

    private static BookingState lockBooking(Connection connection, int bookingId) throws SQLException {
        String sql = "SELECT b.room_id,b.check_out_date,b.total_price,b.status,b.rental_type,r.room_type_id "
                + "FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.id=? FOR UPDATE";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setInt(1, bookingId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("Booking not found");
                return new BookingState(row.getInt("room_id"), row.getTimestamp("check_out_date").toLocalDateTime(),
                        row.getBigDecimal("total_price"), row.getString("status"), row.getString("rental_type"),
                        row.getInt("room_type_id"));
            }
        }
    }

    private static boolean hasRedeemedPromotion(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM promotion_redemptions WHERE booking_id=? LIMIT 1")) {
            query.setInt(1, bookingId);
            try (ResultSet row = query.executeQuery()) { return row.next(); }
        }
    }

    private static FolioState lockMasterFolio(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id,balance FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1 FOR UPDATE")) {
            query.setInt(1, bookingId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Master folio not found");
                return new FolioState(row.getLong("id"), row.getBigDecimal("balance"));
            }
        }
    }

    private static BigDecimal masterFolioBalance(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT balance FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1")) {
            query.setInt(1, bookingId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Master folio not found");
                return row.getBigDecimal(1);
            }
        }
    }

    private static void postCharge(Connection connection, long folioId, String chargeType,
            String description, BigDecimal amount, String reference) throws SQLException {
        if (amount.signum() <= 0) return;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) "
              + "SELECT ?,'debit',?,?,?,business_date,? FROM hotel_business_state WHERE id=1")) {
            insert.setLong(1, folioId);
            insert.setString(2, chargeType);
            insert.setString(3, description);
            insert.setBigDecimal(4, amount);
            insert.setString(5, reference);
            if (insert.executeUpdate() != 1) throw new SQLException("Hotel business date missing; extension charge was not posted");
        }
    }

    public record Result(LocalDateTime checkOut, BigDecimal additionalAmount, BigDecimal folioBalance,
            boolean alreadyApplied) { }
    private record BookingState(int roomId, LocalDateTime checkOut, BigDecimal totalPrice, String status,
            String rentalType, int roomTypeId) { }
    private record FolioState(long id, BigDecimal balance) { }
}
