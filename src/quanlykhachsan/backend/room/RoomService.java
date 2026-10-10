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
        String st = r.getStatus() != null ? r.getStatus().toLowerCase() : "available";
        if (st.equals("maintenance") || st.equals("out_of_service") || st.equals("cleaning")) {
            return; // keep physical status
        }
        // compute based on active bookings
        List<Booking> bookings = bookingDAO.findByRoomId(r.getId());
        for (Booking b : bookings) {
            String bSt = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
            if (bSt.equals("checked_in") || bSt.equals("confirmed") || bSt.equals("pending") || bSt.equals("booked")) {
                if (b.getCheckInDate() != null && b.getCheckOutDate() != null) {
                    if (now >= b.getCheckInDate().getTime() && now <= b.getCheckOutDate().getTime()) {
                        if (bSt.equals("checked_in")) {
                            r.setStatus("occupied");
                        } else {
                            r.setStatus("booked");
                        }
                        return;
                    }
                }
            }
        }
        r.setStatus("available");
    }

    public List<Room> getAllRooms() {
        List<Room> rooms = roomDAO.findAll();
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
        try {
            roomDAO.addRoom(room);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteRoom(int roomId) {
        try {
            Room r = roomDAO.findById(roomId);
            if (r != null) {
                roomDAO.deleteRoom(r);
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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