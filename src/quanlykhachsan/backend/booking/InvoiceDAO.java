package quanlykhachsan.backend.booking;

import java.util.List;
import quanlykhachsan.backend.booking.Invoice;

public interface InvoiceDAO {
    List<Invoice> getAllInvoices();
    List<Invoice> searchInvoices(String keyword);
    boolean addInvoice(Invoice invoice);
}
