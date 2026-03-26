package quanlykhachsan.backend.DAO;

import quanlykhachsan.backend.MODEL.Booking;
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

}
