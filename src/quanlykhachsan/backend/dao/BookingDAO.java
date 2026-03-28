package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Booking;
import java.util.ArrayList;

public interface BookingDAO {

    public int addBooking(Booking booking); // Change return type to int

    public void updateBooking(Booking booking);

    public void deleteBooking(Booking booking);

    public ArrayList<Booking> selectBooking();

    public void comboBoxBooking();

    public java.util.List<Booking> findByRoomId(int roomId);
    public java.util.List<Booking> findByCustomerId(int customerId);
    public boolean insert(Booking booking);
}
