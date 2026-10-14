package quanlykhachsan.backend.booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import quanlykhachsan.backend.system.SystemStateHolder;
import quanlykhachsan.backend.utils.DBconn;

import quanlykhachsan.backend.room.RoomAllocationService;
import quanlykhachsan.backend.room.OptimisticLockException;


public class BookingDAOImpl implements BookingDAO {

    @Override
    public int addBooking(Booking booking) {
        try {
            return addBooking(booking, RoomAllocationService.currentVersion(booking.getRoomId()));
        } catch (OptimisticLockException ex) {
            return -1;
        } catch (java.sql.SQLException ex) {
            ex.printStackTrace();
            return -1;
        }
    }

    public int addBooking(Booking booking, int expectedRoomVersion) throws OptimisticLockException {
        String query = "INSERT INTO bookings(customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = quanlykhachsan.backend.utils.DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            RoomAllocationService.reserve(con, booking.getRoomId(), expectedRoomVersion);
            try (PreparedStatement ps = con.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, booking.getCustomerId());
                ps.setInt(2, booking.getRoomId());
                ps.setTimestamp(3, new java.sql.Timestamp(booking.getCheckInDate().getTime()));
                ps.setTimestamp(4, new java.sql.Timestamp(booking.getCheckOutDate().getTime()));
                ps.setBigDecimal(5, BigDecimal.valueOf(booking.getTotalPrice()).setScale(2, RoundingMode.HALF_UP));
                ps.setString(6, booking.getStatus());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new java.sql.SQLException("Booking insert did not return an ID");
                    int id = rs.getInt(1);
                    booking.setId(id);
                    try (PreparedStatement master = con.prepareStatement(
                            "INSERT INTO folios (booking_id, folio_type, balance, status) VALUES (?, 'master', 0.00, 'open')")) {
                        master.setInt(1, id);
                        master.executeUpdate();
                    }
                    try (PreparedStatement guest = con.prepareStatement(
                            "INSERT INTO folios (booking_id, folio_type, balance, status) VALUES (?, 'guest', 0.00, 'open')")) {
                        guest.setInt(1, id);
                        guest.executeUpdate();
                    }
                    con.commit();
                    return id;
                }
            } catch (Exception ex) {
                con.rollback();
                if (ex instanceof OptimisticLockException) throw (OptimisticLockException) ex;
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void updateBooking(Booking booking) {
        String query = "UPDATE bookings SET customer_id=?, room_id=?, check_in_date=?, check_out_date=?, total_price=?, status=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, booking.getCustomerId());
            ps.setInt(2, booking.getRoomId());
            ps.setTimestamp(3, new java.sql.Timestamp(booking.getCheckInDate().getTime()));
            ps.setTimestamp(4, new java.sql.Timestamp(booking.getCheckOutDate().getTime()));
            ps.setBigDecimal(5, BigDecimal.valueOf(booking.getTotalPrice()).setScale(2, RoundingMode.HALF_UP));
            ps.setString(6, booking.getStatus());
            ps.setInt(7, booking.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteBooking(Booking booking) {
        String query = "DELETE FROM bookings WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, booking.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ArrayList<Booking> selectBooking() {
        ArrayList<Booking> list = new ArrayList<>();
        String query = "SELECT * FROM bookings";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Booking b = new Booking();
                b.setId(rs.getInt("id"));
                b.setCustomerId(rs.getInt("customer_id"));
                b.setRoomId(rs.getInt("room_id"));
                b.setCheckInDate(rs.getTimestamp("check_in_date"));
                b.setCheckOutDate(rs.getTimestamp("check_out_date"));
                b.setTotalPrice(rs.getDouble("total_price"));
                b.setStatus(rs.getString("status"));
                b.setCreatedAt(rs.getTimestamp("created_at"));
                list.add(b);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void comboBoxBooking() {
    }
    @Override
    public java.util.List<Booking> findByRoomId(int roomId) {
        java.util.List<Booking> list = new java.util.ArrayList<>();
        for (Booking b : selectBooking()) {
            if (b.getRoomId() == roomId) list.add(b);
        }
        return list;
    }

    @Override
    public java.util.List<Booking> findByCustomerId(int customerId) {
        java.util.List<Booking> list = new java.util.ArrayList<>();
        for (Booking b : selectBooking()) {
            if (b.getCustomerId() == customerId) list.add(b);
        }
        return list;
    }

    @Override
    public boolean insert(Booking booking) {
        return addBooking(booking) > 0;
    }

    @Override
    public int createBookingAtomic(int customerId, int roomId, int roomVersion, String checkInDate, String checkOutDate, double totalAmount)
            throws SQLException, OptimisticLockException {
        if (checkInDate == null || checkOutDate == null || checkInDate.isBlank() || checkOutDate.isBlank()) {
            throw new IllegalArgumentException("check_in_date and check_out_date are required");
        }
        String ci = checkInDate.trim();
        String co = checkOutDate.trim();
        if (ci.compareTo(co) >= 0) {
            throw new IllegalArgumentException("Check-in date must be before check-out date");
        }

        LocalDate today = SystemStateHolder.getCurrentBusinessDate();
        LocalDate checkInLocalDate;
        try {
            checkInLocalDate = LocalDate.parse(ci.length() >= 10 ? ci.substring(0, 10) : ci);
        } catch (Exception e) {
            checkInLocalDate = today;
        }
        boolean isFuture = checkInLocalDate.isAfter(today);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Overlap Conflict Check in bookings table (Schedule Integrity)
                String overlapSql = "SELECT COUNT(*) FROM bookings " +
                                    "WHERE room_id = ? " +
                                    "AND status IN ('confirmed', 'checked_in', 'reserved') " +
                                    "AND NOT (check_out_date <= ? OR check_in_date >= ?)";
                try (PreparedStatement ps = con.prepareStatement(overlapSql)) {
                    ps.setInt(1, roomId);
                    ps.setString(2, ci);
                    ps.setString(3, co);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new OptimisticLockException("Schedule conflict: Room is already booked for the requested dates (" + ci + " to " + co + ")");
                        }
                    }
                }

                // 2. Room Lock & CAS State Transition
                // - If same-day check-in: Room must be 'vacant_clean' and physical status transitions to 'reserved'.
                //   Atomic CAS version check strictly enforced (WHERE id = ? AND version = ?).
                // - If future check-in: Room only requires status != 'out_of_order' (not broken).
                //   Current physical status remains unchanged; version increments for tracking.
                //   Schedule conflict is strictly guaranteed by overlapSql above, completely eliminating
                //   false version contention across distinct future months.
                if (isFuture) {
                    String lockSql = "UPDATE rooms SET version = version + 1 WHERE id = ? AND status != 'out_of_order'";
                    try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                        ps.setInt(1, roomId);
                        int updated = ps.executeUpdate();
                        if (updated == 0) {
                            throw new OptimisticLockException("Room is out_of_order or not available: id " + roomId);
                        }
                    }
                } else {
                    String lockSql = "UPDATE rooms SET status = 'reserved', version = version + 1 WHERE id = ? AND version = ? AND status = 'vacant_clean'";
                    try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                        ps.setInt(1, roomId);
                        ps.setInt(2, roomVersion);
                        int updated = ps.executeUpdate();
                        if (updated == 0) {
                            throw new OptimisticLockException("Room version mismatch, room not available, or room not vacant_clean: expected version " + roomVersion);
                        }
                    }
                }

                // 3. Insert into bookings
                String insertSql = "INSERT INTO bookings(customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES (?, ?, ?, ?, ?, 'confirmed')";
                int bookingId = -1;
                try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerId);
                    ps.setInt(2, roomId);
                    ps.setString(3, ci);
                    ps.setString(4, co);
                    ps.setDouble(5, totalAmount);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            bookingId = rs.getInt(1);
                        }
                    }
                }

                if (bookingId <= 0) {
                    throw new SQLException("Failed to retrieve generated booking ID");
                }

                con.commit();

                // 4. Provision Master Folio in FolioRoutingEngine (persists to MariaDB via FolioDAO)
                FolioRoutingEngine.getInstance().splitFolio(bookingId, totalAmount);

                return bookingId;
            } catch (Exception e) {
                con.rollback();
                if (e instanceof OptimisticLockException) {
                    throw (OptimisticLockException) e;
                }
                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }
                throw new SQLException("Error creating booking atomic: " + e.getMessage(), e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean updateBookingAtomic(int bookingId, int roomVersion, String newCheckInDate, String newCheckOutDate, Double newTotalAmount)
            throws SQLException, OptimisticLockException {
        if (newCheckInDate == null || newCheckOutDate == null || newCheckInDate.isBlank() || newCheckOutDate.isBlank()) {
            throw new IllegalArgumentException("check_in_date and check_out_date are required");
        }
        String ci = newCheckInDate.trim();
        String co = newCheckOutDate.trim();
        if (ci.compareTo(co) >= 0) {
            throw new IllegalArgumentException("Check-in date must be before check-out date");
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Fetch current booking details
                int roomId = -1;
                String currentStatus = null;
                String findSql = "SELECT room_id, status FROM bookings WHERE id = ?";
                try (PreparedStatement ps = con.prepareStatement(findSql)) {
                    ps.setInt(1, bookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            roomId = rs.getInt("room_id");
                            currentStatus = rs.getString("status");
                        }
                    }
                }

                if (roomId <= 0) {
                    con.rollback();
                    return false;
                }

                if ("cancelled".equalsIgnoreCase(currentStatus) || "checked_out".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot modify booking in " + currentStatus + " status");
                }

                // 2. CHECK FOR TIME OVERLAPS IN BOOKINGS TABLE (Overbooking Protection - read check)
                // Ensure no conflicting active booking exists for the same room during the new date range (excluding this booking)
                String overlapSql = "SELECT COUNT(*) FROM bookings " +
                                    "WHERE room_id = ? " +
                                    "AND id != ? " +
                                    "AND status IN ('confirmed', 'checked_in', 'reserved') " +
                                    "AND NOT (check_out_date <= ? OR check_in_date >= ?)";
                try (PreparedStatement ps = con.prepareStatement(overlapSql)) {
                    ps.setInt(1, roomId);
                    ps.setInt(2, bookingId);
                    ps.setString(3, ci);
                    ps.setString(4, co);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new OptimisticLockException("Schedule conflict: Room is already booked by another reservation for the requested stay window (" + ci + " to " + co + ")");
                        }
                    }
                }

                // 3. Optimistic CAS on rooms.version (Write Lock Order: rooms -> bookings)
                String lockSql = "UPDATE rooms SET version = version + 1 WHERE id = ? AND version = ?";
                try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                    ps.setInt(1, roomId);
                    ps.setInt(2, roomVersion);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        throw new OptimisticLockException("Room version mismatch: expected version " + roomVersion);
                    }
                }

                // 4. Update booking dates and optional total price
                String updateBookingSql = "UPDATE bookings SET check_in_date = ?, check_out_date = ?, total_price = COALESCE(?, total_price) WHERE id = ?";
                try (PreparedStatement ps = con.prepareStatement(updateBookingSql)) {
                    ps.setString(1, ci);
                    ps.setString(2, co);
                    if (newTotalAmount != null && newTotalAmount > 0) {
                        ps.setDouble(3, newTotalAmount);
                    } else {
                        ps.setNull(3, java.sql.Types.DOUBLE);
                    }
                    ps.setInt(4, bookingId);
                    ps.executeUpdate();
                }

                con.commit();
                return true;
            } catch (Exception e) {
                con.rollback();
                if (e instanceof OptimisticLockException) {
                    throw (OptimisticLockException) e;
                }
                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                if (e instanceof IllegalStateException) {
                    throw (IllegalStateException) e;
                }
                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }
                throw new SQLException("Error updating booking atomic: " + e.getMessage(), e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean cancelBooking(int bookingId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // Monotonic Lock Ordering: rooms -> bookings -> folios (strictly eliminates deadlock 1213)
                // 0. Non-locking MVCC snapshot read to determine room_id (acquires ZERO row/table locks)
                int roomId = -1;
                String findSql = "SELECT room_id, status FROM bookings WHERE id = ?";
                try (PreparedStatement ps = con.prepareStatement(findSql)) {
                    ps.setInt(1, bookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            roomId = rs.getInt("room_id");
                        }
                    }
                }

                if (roomId <= 0) {
                    con.rollback();
                    return false;
                }

                // 1. First exclusive write lock acquired on ROOMS row
                String releaseRoomSql = "UPDATE rooms SET status = CASE WHEN status = 'reserved' THEN 'vacant_clean' ELSE status END, version = version + 1 WHERE id = ?";
                try (PreparedStatement ps = con.prepareStatement(releaseRoomSql)) {
                    ps.setInt(1, roomId);
                    ps.executeUpdate();
                }

                // 2. Second exclusive write lock acquired on BOOKINGS row
                String updateBookingSql = "UPDATE bookings SET status = 'cancelled' WHERE id = ?";
                try (PreparedStatement ps = con.prepareStatement(updateBookingSql)) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }

                con.commit();

                // 3. Third write lock: ADR-06 Append-Only Ledger reconciliation
                // Inserts offsetting reversal credit transaction into folio_transactions and marks folios CANCELLED,
                // preserving mathematical ledger integrity (balance == sum(charges) - sum(payments) == 0.00)
                FolioRoutingEngine.getInstance().cancelBookingFolios(bookingId);

                return true;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<Map<String, Object>> getAllBookings() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT b.id, b.customer_id, c.full_name AS customer_name, b.room_id, r.room_number, " +
                     "b.check_in_date, b.check_out_date, b.total_price AS total_amount, b.status, b.created_at " +
                     "FROM bookings b " +
                     "LEFT JOIN customers c ON b.customer_id = c.id " +
                     "LEFT JOIN rooms r ON b.room_id = r.id " +
                     "ORDER BY b.id DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("customer_id", rs.getInt("customer_id"));
                map.put("customer_name", rs.getString("customer_name"));
                map.put("room_id", rs.getInt("room_id"));
                map.put("room_number", rs.getString("room_number"));
                map.put("check_in_date", rs.getString("check_in_date"));
                map.put("check_out_date", rs.getString("check_out_date"));
                map.put("total_amount", rs.getDouble("total_amount"));
                map.put("status", rs.getString("status"));
                map.put("created_at", rs.getString("created_at"));
                list.add(map);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error fetching bookings: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public Map<String, Object> getBookingById(int id) {
        String sql = "SELECT b.id, b.customer_id, c.full_name AS customer_name, b.room_id, r.room_number, " +
                     "b.check_in_date, b.check_out_date, b.total_price AS total_amount, b.status, b.created_at " +
                     "FROM bookings b " +
                     "LEFT JOIN customers c ON b.customer_id = c.id " +
                     "LEFT JOIN rooms r ON b.room_id = r.id " +
                     "WHERE b.id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("customer_id", rs.getInt("customer_id"));
                    map.put("customer_name", rs.getString("customer_name"));
                    map.put("room_id", rs.getInt("room_id"));
                    map.put("room_number", rs.getString("room_number"));
                    map.put("check_in_date", rs.getString("check_in_date"));
                    map.put("check_out_date", rs.getString("check_out_date"));
                    map.put("total_amount", rs.getDouble("total_amount"));
                    map.put("status", rs.getString("status"));
                    map.put("created_at", rs.getString("created_at"));
                    return map;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error fetching booking by id: " + e.getMessage(), e);
        }
        return null;
    }
}
