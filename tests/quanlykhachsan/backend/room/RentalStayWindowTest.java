package quanlykhachsan.backend.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class RentalStayWindowTest {
    @Test void hourlyBookingUnderSixHoursKeepsHourlyPackageAndBuffer() {
        LocalDate day = LocalDate.now().plusDays(2);
        StayWindow stay = StayWindow.resolve(RentalType.HOURLY, day.atTime(18, 0), day.atTime(22, 0));
        assertEquals(RentalType.HOURLY, stay.rentalType());
        assertEquals(day.atTime(22, 30), stay.releaseAt(30));
    }

    @Test void longHourlyBookingThatCoversNightConvertsToOvernight() {
        LocalDate day = LocalDate.now().plusDays(2);
        StayWindow stay = StayWindow.resolve(RentalType.HOURLY, day.atTime(18, 0), day.plusDays(1).atTime(8, 0));
        assertEquals(RentalType.OVERNIGHT, stay.rentalType());
        assertEquals(6, stay.billableHourlyAddOns());
        assertEquals(day.plusDays(1).atTime(9, 0), stay.releaseAt(60));
    }

    @Test void longHourlyDaytimeRequestConvertsToDailyWindow() {
        LocalDate day = LocalDate.now().plusDays(2);
        StayWindow stay = StayWindow.resolve(RentalType.HOURLY, day.atTime(8, 0), day.atTime(16, 0));
        assertEquals(RentalType.DAILY, stay.rentalType());
        assertEquals(day.atTime(14, 0), stay.checkIn());
        assertEquals(day.plusDays(1).atTime(12, 0), stay.checkOut());
        assertEquals(LocalDateTime.of(day.plusDays(1), LocalTime.of(14, 0)), stay.releaseAt(120));
    }
}
