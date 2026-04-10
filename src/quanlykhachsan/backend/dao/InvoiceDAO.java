package quanlykhachsan.backend.dao;

import java.util.List;
import quanlykhachsan.backend.model.Invoice;

public interface InvoiceDAO {
    List<Invoice> getAllInvoices();
    List<Invoice> searchInvoices(String keyword);
    boolean addInvoice(Invoice invoice);
}
