package quanlykhachsan.backend.booking;

import java.util.List;
import quanlykhachsan.backend.booking.Invoice;

public interface InvoiceDAO {
    List<Invoice> getAllInvoices();
    List<Invoice> searchInvoices(String keyword);
    List<Invoice> searchInvoices(String keyword, int offset, int limit);
    List<Invoice> searchInvoices(String keyword, String status, String fromDate, String toDate, int offset, int limit);
    boolean addInvoice(Invoice invoice);
    Invoice getInvoiceById(int id);
}
