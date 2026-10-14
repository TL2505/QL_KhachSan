package quanlykhachsan.backend.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quanlykhachsan.backend.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Production MariaDB implementation of FolioDAO (TASK-B2.2 / TASK-B3.2 / ADR-06).
 * Persists folios and ledger transactions directly into the transactional database.
 */
public class FolioDAOImpl implements FolioDAO {

    private static final Logger logger = LoggerFactory.getLogger(FolioDAOImpl.class);

    @Override
    public void saveOrUpdateFolio(FolioRoutingEngine.Folio folio) {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return;
        }

        String sql = "INSERT INTO folios (id, booking_id, folio_type, status, balance, paid_amount) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE status = VALUES(status), balance = VALUES(balance), paid_amount = VALUES(paid_amount)";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, folio.getFolioId());
            ps.setInt(2, folio.getBookingId());
            ps.setString(3, folio.getType().name());
            ps.setString(4, folio.getStatus().name());
            ps.setDouble(5, folio.getBalance());
            ps.setDouble(6, folio.getPaidAmount());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save or update folio {}: {}", folio.getFolioId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving folio " + folio.getFolioId(), e);
        }
    }

    @Override
    public FolioRoutingEngine.Folio findFolioById(String folioId) {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return null;
        }

        String sql = "SELECT id, booking_id, folio_type, status, balance, paid_amount FROM folios WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, folioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    int bookingId = rs.getInt("booking_id");
                    String typeStr = rs.getString("folio_type");
                    String statusStr = rs.getString("status");
                    double balance = rs.getDouble("balance");
                    double paidAmount = rs.getDouble("paid_amount");

                    FolioRoutingEngine.FolioType type = "MASTER".equalsIgnoreCase(typeStr)
                            ? FolioRoutingEngine.FolioType.MASTER : FolioRoutingEngine.FolioType.GUEST;
                    FolioRoutingEngine.Folio folio = new FolioRoutingEngine.Folio(id, bookingId, type);

                    // Rehydrate status and balances
                    try {
                        folio.status = FolioRoutingEngine.FolioStatus.valueOf(statusStr.toUpperCase());
                    } catch (Exception ignored) {
                        folio.status = FolioRoutingEngine.FolioStatus.OPEN;
                    }
                    folio.balance = balance;
                    folio.paidAmount = paidAmount;

                    // Rehydrate charges
                    List<FolioRoutingEngine.ChargeItem> charges = findTransactionsByFolioId(folioId);
                    for (FolioRoutingEngine.ChargeItem chg : charges) {
                        folio.charges.add(chg);
                    }

                    return folio;
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving folio by ID {}: {}", folioId, e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<FolioRoutingEngine.Folio> findFoliosByBookingId(int bookingId) {
        List<FolioRoutingEngine.Folio> list = new ArrayList<>();
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return list;
        }

        String sql = "SELECT id FROM folios WHERE booking_id = ? ORDER BY id ASC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String folioId = rs.getString("id");
                    FolioRoutingEngine.Folio f = findFolioById(folioId);
                    if (f != null) {
                        list.add(f);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving folios for booking ID {}: {}", bookingId, e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void addTransaction(FolioRoutingEngine.ChargeItem item) {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return;
        }

        String sql = "INSERT INTO folio_transactions (id, folio_id, category, description, amount, business_date, offline_timestamp) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item.getChargeId());
            ps.setString(2, item.getRoutedFolioId());
            ps.setString(3, item.getCategory().name());
            ps.setString(4, item.getDescription());
            ps.setDouble(5, item.getAmount());
            ps.setString(6, item.getBusinessDate());
            ps.setString(7, item.getOfflineTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error inserting ledger transaction {}: {}", item.getChargeId(), e.getMessage(), e);
            throw new RuntimeException("Database error adding folio transaction " + item.getChargeId(), e);
        }
    }

    @Override
    public List<FolioRoutingEngine.ChargeItem> findTransactionsByFolioId(String folioId) {
        List<FolioRoutingEngine.ChargeItem> list = new ArrayList<>();
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return list;
        }

        String sql = "SELECT id, folio_id, category, description, amount, business_date, offline_timestamp, created_at "
                + "FROM folio_transactions WHERE folio_id = ? ORDER BY created_at ASC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, folioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String catStr = rs.getString("category");
                    String desc = rs.getString("description");
                    double amount = rs.getDouble("amount");
                    String bDate = rs.getString("business_date");
                    String offTs = rs.getString("offline_timestamp");
                    String createdAt = rs.getString("created_at");

                    FolioRoutingEngine.ChargeCategory cat;
                    try {
                        cat = FolioRoutingEngine.ChargeCategory.valueOf(catStr);
                    } catch (Exception e) {
                        cat = FolioRoutingEngine.ChargeCategory.INCIDENTAL;
                    }

                    list.add(new FolioRoutingEngine.ChargeItem(id, cat, desc, amount, folioId, bDate, createdAt, offTs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying transactions for folio {}: {}", folioId, e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void updateFolioBalanceAndPayment(String folioId, double balance, double paidAmount, FolioRoutingEngine.FolioStatus status) {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return;
        }

        String sql = "UPDATE folios SET balance = ?, paid_amount = ?, status = ? WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, balance);
            ps.setDouble(2, paidAmount);
            ps.setString(3, status.name());
            ps.setString(4, folioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating balance for folio {}: {}", folioId, e.getMessage(), e);
            throw new RuntimeException("Database error updating folio balance for " + folioId, e);
        }
    }

    @Override
    public void cancelFoliosByBookingId(int bookingId) {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return;
        }

        List<FolioRoutingEngine.Folio> folios = findFoliosByBookingId(bookingId);
        for (FolioRoutingEngine.Folio folio : folios) {
            if (folio.getPaidAmount() == 0.0) {
                if (folio.getBalance() > 0.0) {
                    // ADR-06: Append offsetting reversal credit transaction into folio_transactions
                    double reversalAmount = -folio.getBalance();
                    FolioRoutingEngine.ChargeItem reversal = new FolioRoutingEngine.ChargeItem(
                            "REV-" + System.currentTimeMillis() + "-" + Math.abs(folio.getFolioId().hashCode() % 10000),
                            FolioRoutingEngine.ChargeCategory.ROOM_RATE,
                            "Booking Cancellation - Full Ledger Reversal",
                            reversalAmount,
                            folio.getFolioId(),
                            quanlykhachsan.backend.system.SystemStateHolder.getCurrentBusinessDate().toString(),
                            java.time.Instant.now().toString(),
                            null
                    );
                    addTransaction(reversal);
                    updateFolioBalanceAndPayment(folio.getFolioId(), 0.00, folio.getPaidAmount(), FolioRoutingEngine.FolioStatus.CANCELLED);
                } else {
                    updateFolioBalanceAndPayment(folio.getFolioId(), 0.00, folio.getPaidAmount(), FolioRoutingEngine.FolioStatus.CANCELLED);
                }
            }
        }
    }
}
