package quanlykhachsan.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.backend.hotelservice.*;

class ServiceOrderValidationTest {
    private ServiceOrder order(){var o=new ServiceOrder();o.setBookingId(1);o.setCategory("FNB");o.setServiceId(1);o.setQuantity(1);return o;}
    @Test void rejectsInvalidQuantitiesCategoriesAndNonCatalogCharges(){
        var o=order();assertDoesNotThrow(()->ServiceOrderDAO.validate(o));
        o.setQuantity(-1);assertThrows(IllegalArgumentException.class,()->ServiceOrderDAO.validate(o));
        o.setQuantity(101);assertThrows(IllegalArgumentException.class,()->ServiceOrderDAO.validate(o));
        o.setQuantity(1);o.setServiceId(null);assertThrows(IllegalArgumentException.class,()->ServiceOrderDAO.validate(o));
        o.setCategory("SPA");assertThrows(IllegalArgumentException.class,()->ServiceOrderDAO.validate(o));
        o.setCategory("HOUSEKEEPING");o.setItemName("Fresh towels");assertDoesNotThrow(()->ServiceOrderDAO.validate(o));
        o.setScheduledTime(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(10)));assertThrows(IllegalArgumentException.class,()->ServiceOrderDAO.validate(o));
        o.setScheduledTime(null);o.setCategory("INVALID");assertThrows(IllegalArgumentException.class,()->ServiceOrderDAO.validate(o));
    }
}
