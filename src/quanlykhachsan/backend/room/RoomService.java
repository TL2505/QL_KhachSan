package quanlykhachsan.backend.room;

import quanlykhachsan.backend.booking.BookingDAO;
import quanlykhachsan.backend.booking.BookingDAOImpl;
import quanlykhachsan.backend.booking.Booking;
import java.util.List;

public class RoomService {

    private RoomDAO roomDAO = new RoomDAOImpl();
    private RoomTypeDAO roomTypeDAO = new RoomTypeDAOImpl();
    private BookingDAO bookingDAO = new BookingDAOImpl();

    private void computeVirtualStatus(Room r, long now) {
        String st = r.getStatus() != null ? r.getStatus().toLowerCase() : "vacant_clean";
        if ("out_of_order".equals(st) || "vacant_dirty".equals(st)
                || "occupied_dirty".equals(st)) return;
        // compute based on active bookings
        List<Booking> bookings = bookingDAO.findByRoomId(r.getId());
        for (Booking b : bookings) {
            String bSt = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
            if (bSt.equals("checked_in") || bSt.equals("confirmed") || bSt.equals("pending") || bSt.equals("booked")) {
                if (b.getCheckInDate() != null && b.getCheckOutDate() != null) {
                    if (now >= b.getCheckInDate().getTime() && now <= b.getCheckOutDate().getTime()) {
                        if (bSt.equals("checked_in")) {
                            r.setStatus("occupied_clean");
                        } else {
                            r.setStatus("reserved");
                        }
                        return;
                    }
                }
            }
        }
        if (!java.util.Set.of("vacant_clean", "vacant_dirty", "occupied_clean",
                "occupied_dirty", "out_of_order", "reserved").contains(st)) {
            r.setStatus("vacant_clean");
        }
    }

    public List<Room> getAllRooms() {
        return getAllRooms(false);
    }

    public List<Room> getAllRooms(boolean newest) {
        List<Room> rooms = newest ? roomDAO.findAllNewest() : roomDAO.findAll();
        long now = System.currentTimeMillis();
        for (Room r : rooms) {
            computeVirtualStatus(r, now);
        }
        return rooms;
    }

    public Room getRoomById(int id) {
        Room r = roomDAO.findById(id);
        if (r != null) {
            computeVirtualStatus(r, System.currentTimeMillis());
        }
        return r;
    }

    public boolean updateRoomStatus(int roomId, String status) {
        return roomDAO.updateStatus(roomId, status);
    }

    public boolean addRoom(Room room) {
        roomDAO.addRoom(room);
        return true;
    }

    public boolean updateRoom(Room room) {
        return roomDAO.updateRoom(room);
    }

    public boolean deleteRoom(int roomId) {
        Room r = roomDAO.findById(roomId);
        return r != null && roomDAO.deleteRoom(r);
    }

    public List<RoomType> getAllRoomTypes() {
        return roomTypeDAO.findAll();
    }

    public RoomType getRoomTypeById(int id) {
        return roomTypeDAO.findById(id);
    }

    public List<Room> findAvailableRooms(java.util.Date checkIn, java.util.Date checkOut) {
        return roomDAO.findAvailableRooms(checkIn, checkOut);
    }
}
