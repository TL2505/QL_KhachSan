package quanlykhachsan.backend.booking;

import java.util.List;

/**
 * Data Access Object interface for ADR-06 Multi-Folio Ledger Engine (TASK-B2.2 / TASK-B3.2).
 * Eliminates RAM/DB split-brain by persisting Folios and Ledger Line Items to MariaDB.
 */
public interface FolioDAO {

    /**
     * Persists a new folio or updates an existing folio's status and totals.
     */
    void saveOrUpdateFolio(FolioRoutingEngine.Folio folio);

    /**
     * Retrieves a Folio entity and its line-item transactions by its unique ID.
     */
    FolioRoutingEngine.Folio findFolioById(String folioId);

    /**
     * Retrieves all folios associated with a specific booking (e.g. MASTER and GUEST).
     */
    List<FolioRoutingEngine.Folio> findFoliosByBookingId(int bookingId);

    /**
     * Appends an immutable ledger charge transaction record.
     */
    void addTransaction(FolioRoutingEngine.ChargeItem item);

    /**
     * Retrieves all ledger transaction line items for a given folio.
     */
    List<FolioRoutingEngine.ChargeItem> findTransactionsByFolioId(String folioId);

    /**
     * Updates folio balance, paid amount, and status atomically.
     */
    void updateFolioBalanceAndPayment(String folioId, double balance, double paidAmount, FolioRoutingEngine.FolioStatus status);

    /**
     * Cancels and zeroes all unpaid folios for a cancelled booking.
     */
    void cancelFoliosByBookingId(int bookingId);
}
