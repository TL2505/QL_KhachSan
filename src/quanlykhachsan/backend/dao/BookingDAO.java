package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Booking;
import java.util.ArrayList;

public interface BookingDAO {

//    add Booking
    public void addBooking(Booking booking);

//    update Booking
    public void updateBooking(Booking booking);

//    delete Booking
    public void deleteBooking(Booking booking);

//    list of Booking
    public ArrayList<Booking> selectBooking();

    public void comboBoxBooking();

    public java.util.List<Booking> findByRoomId(int roomId);
    public java.util.List<Booking> findByCustomerId(int customerId);
    public boolean insert(Booking booking);
}
