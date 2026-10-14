package quanlykhachsan.backend.booking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * Enterprise Multi-Folio Routing & Bill Splitting Engine (TASK-B3.2 / Scenario 1 / ADR-06).
 * <p>
 * Implements split-folio architecture for individual and group reservations:
 * <ul>
 *   <li><b>FOLIO-MASTER:</b> Automatically aggregates Room Rates, Base Charges, and Taxes.</li>
 *   <li><b>FOLIO-GUEST:</b> Exclusively routes personal Incidentals (Mini-bar, Laundry, F&B Room Service, Spa).</li>
 * </ul>
 * <p>
 * Eliminates RAM/DB split-brain by persisting and restoring state from MariaDB via {@link FolioDAO}.
 */
public class FolioRoutingEngine {

    public enum FolioType {
        MASTER,
        GUEST
    }

    public enum ChargeCategory {
        ROOM_RATE,
        TAX,
        MINI_BAR,
        LAUNDRY,
        ROOM_SERVICE,
        SPA,
        INCIDENTAL
    }

    public enum FolioStatus {
        OPEN,
        SETTLED,
        CLOSED,
        VOID,
        CANCELLED
    }

    public static class ChargeItem {
        private final String chargeId;
        private final ChargeCategory category;
        private final String description;
        private final double amount;
        private final String timestamp;
        private final String routedFolioId;
        private final String businessDate;
        private final String offlineTimestamp;

        public ChargeItem(String chargeId, ChargeCategory category, String description, double amount, String routedFolioId) {
            this(chargeId, category, description, amount, routedFolioId,
                 quanlykhachsan.backend.system.SystemStateHolder.getBusinessDate() != null
                         ? quanlykhachsan.backend.system.SystemStateHolder.getBusinessDate().toString()
                         : java.time.LocalDate.now().toString(),
                 Instant.now().toString(),
                 null);
        }

        public ChargeItem(String chargeId, ChargeCategory category, String description, double amount, String routedFolioId, String businessDate, String timestamp) {
            this(chargeId, category, description, amount, routedFolioId, businessDate, timestamp, null);
        }

        public ChargeItem(String chargeId, ChargeCategory category, String description, double amount, String routedFolioId, String businessDate, String timestamp, String offlineTimestamp) {
            this.chargeId = chargeId;
            this.category = category;
            this.description = description;
            this.amount = amount;
            this.routedFolioId = routedFolioId;
            this.businessDate = businessDate != null
                    ? businessDate
                    : (quanlykhachsan.backend.system.SystemStateHolder.getBusinessDate() != null
                    ? quanlykhachsan.backend.system.SystemStateHolder.getBusinessDate().toString()
                    : java.time.LocalDate.now().toString());
            this.timestamp = timestamp != null ? timestamp : Instant.now().toString();
            this.offlineTimestamp = offlineTimestamp;
        }

        public String getChargeId() { return chargeId; }
        public ChargeCategory getCategory() { return category; }
        public String getDescription() { return description; }
        public double getAmount() { return amount; }
        public String getTimestamp() { return timestamp; }
        public String getRoutedFolioId() { return routedFolioId; }
        public String getBusinessDate() { return businessDate; }
        public String getOfflineTimestamp() { return offlineTimestamp; }
    }

    public static class Folio {
        private final String folioId;
        private final int bookingId;
        private final FolioType type;
        FolioStatus status;
        double balance;
        double paidAmount;
        final List<ChargeItem> charges = Collections.synchronizedList(new ArrayList<>());

        public Folio(String folioId, int bookingId, FolioType type) {
            this.folioId = folioId;
            this.bookingId = bookingId;
            this.type = type;
            this.status = FolioStatus.OPEN;
            this.balance = 0.0;
            this.paidAmount = 0.0;
        }

        public synchronized void addCharge(ChargeItem item) {
            charges.add(item);
            balance += item.getAmount();
        }

        public synchronized void recordPayment(double amount) {
            this.paidAmount += amount;
            this.balance = Math.max(0.0, this.balance - amount);
            if (this.balance == 0.0) {
                this.status = FolioStatus.SETTLED;
            }
        }

        public synchronized void closeFolio() {
            if (this.status == FolioStatus.CANCELLED || this.status == FolioStatus.VOID) {
                return;
            }
            if (this.balance > 0.0) {
                throw new IllegalStateException("Cannot close folio " + folioId + " with outstanding balance: " + balance);
            }
            this.status = FolioStatus.CLOSED;
        }

        public String getFolioId() { return folioId; }
        public int getBookingId() { return bookingId; }
        public FolioType getType() { return type; }
        public synchronized FolioStatus getStatus() { return status; }
        public synchronized double getBalance() { return balance; }
        public synchronized double getPaidAmount() { return paidAmount; }
        public List<ChargeItem> getCharges() { return new ArrayList<>(charges); }
    }

    public static class SplitRoutingPair {
        private final String masterFolioId;
        private final String guestFolioId;
        private final int bookingId;

        public SplitRoutingPair(String masterFolioId, String guestFolioId, int bookingId) {
            this.masterFolioId = masterFolioId;
            this.guestFolioId = guestFolioId;
            this.bookingId = bookingId;
        }

        public String getMasterFolioId() { return masterFolioId; }
        public String getGuestFolioId() { return guestFolioId; }
        public int getBookingId() { return bookingId; }
    }

    private static final AtomicLong chargeSeq = new AtomicLong(1000);
    private final Map<String, Folio> folios = new ConcurrentHashMap<>();
    private final Map<String, SplitRoutingPair> routingPairsByMaster = new ConcurrentHashMap<>();
    private final Map<String, SplitRoutingPair> routingPairsByGuest = new ConcurrentHashMap<>();
    private final Map<Integer, SplitRoutingPair> routingPairsByBooking = new ConcurrentHashMap<>();
    private final Map<String, List<String>> groupMasterToGuests = new ConcurrentHashMap<>();

    private final FolioDAO folioDao = new FolioDAOImpl();

    private static final FolioRoutingEngine INSTANCE = new FolioRoutingEngine();

    public static FolioRoutingEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Splits a booking's folio into a dual-folio structure:
     * FOLIO-MASTER (Room rates, taxes) and FOLIO-GUEST (Incidentals).
     * Automatically persists into MariaDB when database is available.
     */
    public synchronized SplitRoutingPair splitFolio(int bookingId, double roomRate) {
        String masterId = "FOLIO-MASTER-" + bookingId;
        String guestId = "FOLIO-GUEST-" + bookingId;

        Folio master = new Folio(masterId, bookingId, FolioType.MASTER);
        Folio guest = new Folio(guestId, bookingId, FolioType.GUEST);

        ChargeItem roomCharge = null;
        if (roomRate > 0) {
            roomCharge = new ChargeItem(
                    "CHG-" + chargeSeq.incrementAndGet(),
                    ChargeCategory.ROOM_RATE,
                    "Contracted Room Rate & Lodging",
                    roomRate,
                    masterId
            );
            master.addCharge(roomCharge);
        }

        folios.put(masterId, master);
        folios.put(guestId, guest);

        SplitRoutingPair pair = new SplitRoutingPair(masterId, guestId, bookingId);
        routingPairsByMaster.put(masterId, pair);
        routingPairsByGuest.put(guestId, pair);
        routingPairsByBooking.put(bookingId, pair);

        // MariaDB persistence: eliminate split-brain across container restarts
        if (DatabaseConnection.isDatabaseAvailable()) {
            try {
                folioDao.saveOrUpdateFolio(master);
                folioDao.saveOrUpdateFolio(guest);
                if (roomCharge != null) {
                    folioDao.addTransaction(roomCharge);
                }
            } catch (Exception ignored) {}
        }

        return pair;
    }

    /**
     * Registers a Group-level Master Folio aggregating multiple room bookings,
     * while maintaining isolated guest incidentals across all rooms.
     */
    public synchronized SplitRoutingPair splitGroupFolio(String groupMasterFolioId, List<Integer> bookingIds, double totalRoomRate) {
        Folio groupMaster = new Folio(groupMasterFolioId, 0, FolioType.MASTER);
        ChargeItem roomCharge = null;
        if (totalRoomRate > 0) {
            roomCharge = new ChargeItem(
                    "CHG-" + chargeSeq.incrementAndGet(),
                    ChargeCategory.ROOM_RATE,
                    "Group Room Charges (" + bookingIds.size() + " Rooms)",
                    totalRoomRate,
                    groupMasterFolioId
            );
            groupMaster.addCharge(roomCharge);
        }
        folios.put(groupMasterFolioId, groupMaster);

        List<String> guestFolioIds = new ArrayList<>();
        for (int bId : bookingIds) {
            SplitRoutingPair pair = splitFolio(bId, totalRoomRate / Math.max(1, bookingIds.size()));
            guestFolioIds.add(pair.getGuestFolioId());
        }
        groupMasterToGuests.put(groupMasterFolioId, guestFolioIds);

        if (DatabaseConnection.isDatabaseAvailable()) {
            try {
                folioDao.saveOrUpdateFolio(groupMaster);
                if (roomCharge != null) {
                    folioDao.addTransaction(roomCharge);
                }
            } catch (Exception ignored) {}
        }

        return new SplitRoutingPair(groupMasterFolioId, guestFolioIds.isEmpty() ? "" : guestFolioIds.get(0), 0);
    }

    /**
     * Intelligently routes a charge according to Scenario 1 rules:
     * <ul>
     *   <li>Incidentals (Mini-bar, Laundry, Room Service, Spa) -> {@code FOLIO-GUEST}</li>
     *   <li>Room rates and taxes -> {@code FOLIO-MASTER}</li>
     * </ul>
     */
    public synchronized ChargeItem routeChargeByBooking(int bookingId, ChargeCategory category, String description, double amount) {
        SplitRoutingPair pair = getPairByBooking(bookingId);
        if (pair == null) {
            pair = splitFolio(bookingId, 0.0);
        }

        String targetFolioId;
        if (isIncidental(category)) {
            targetFolioId = pair.getGuestFolioId();
        } else {
            targetFolioId = pair.getMasterFolioId();
        }

        ChargeItem charge = new ChargeItem(
                "CHG-" + chargeSeq.incrementAndGet(),
                category,
                description,
                amount,
                targetFolioId
        );

        Folio targetFolio = getFolio(targetFolioId);
        if (targetFolio != null) {
            targetFolio.addCharge(charge);
        }

        if (DatabaseConnection.isDatabaseAvailable()) {
            try {
                folioDao.addTransaction(charge);
                if (targetFolio != null) {
                    folioDao.updateFolioBalanceAndPayment(targetFolioId, targetFolio.getBalance(), targetFolio.getPaidAmount(), targetFolio.getStatus());
                }
            } catch (Exception ignored) {}
        }

        return charge;
    }

    /**
     * Posts a charge directly to a designated Folio with an explicit business date
     * and preserved offline generation timestamp (Scenario 3 / TASK-A3.2).
     */
    public synchronized ChargeItem postChargeToFolio(String folioId, ChargeCategory category, String description, double amount, String businessDate, String offlineTimestamp) {
        Folio targetFolio = getFolio(folioId);
        if (targetFolio == null) {
            targetFolio = new Folio(folioId, 0, FolioType.GUEST);
            folios.put(folioId, targetFolio);
            if (DatabaseConnection.isDatabaseAvailable()) {
                try {
                    folioDao.saveOrUpdateFolio(targetFolio);
                } catch (Exception ignored) {}
            }
        }

        ChargeItem charge = new ChargeItem(
                "CHG-" + chargeSeq.incrementAndGet(),
                category,
                description,
                amount,
                folioId,
                businessDate,
                Instant.now().toString(),
                offlineTimestamp
        );

        targetFolio.addCharge(charge);

        if (DatabaseConnection.isDatabaseAvailable()) {
            try {
                folioDao.addTransaction(charge);
                folioDao.updateFolioBalanceAndPayment(folioId, targetFolio.getBalance(), targetFolio.getPaidAmount(), targetFolio.getStatus());
            } catch (Exception ignored) {}
        }

        return charge;
    }

    public static boolean isIncidental(ChargeCategory category) {
        return category == ChargeCategory.MINI_BAR
                || category == ChargeCategory.LAUNDRY
                || category == ChargeCategory.ROOM_SERVICE
                || category == ChargeCategory.SPA
                || category == ChargeCategory.INCIDENTAL;
    }

    /**
     * Settle Master Folio Guardrail (TASK-B3.2 DoD):
     * Settling Master Folio blocks closure if guest incidentals remain unpaid.
     */
    public synchronized boolean canSettleMasterFolio(String masterFolioId) {
        // 1. Check if it is a Group Master Folio
        List<String> linkedGuests = groupMasterToGuests.get(masterFolioId);
        if (linkedGuests != null) {
            for (String gId : linkedGuests) {
                Folio gf = getFolio(gId);
                if (gf != null && gf.getBalance() > 0.0) {
                    return false;
                }
            }
            return true;
        }

        // 2. Check if it is an individual Booking Master Folio
        SplitRoutingPair pair = routingPairsByMaster.get(masterFolioId);
        if (pair != null) {
            Folio guestFolio = getFolio(pair.getGuestFolioId());
            if (guestFolio != null && guestFolio.getBalance() > 0.0) {
                return false;
            }
        } else if (masterFolioId.startsWith("FOLIO-MASTER-")) {
            try {
                int bId = Integer.parseInt(masterFolioId.replace("FOLIO-MASTER-", ""));
                Folio guestFolio = getFolio("FOLIO-GUEST-" + bId);
                if (guestFolio != null && guestFolio.getBalance() > 0.0) {
                    return false;
                }
            } catch (Exception ignored) {}
        }

        return true;
    }

    /**
     * Retrieves a Folio by ID from memory, lazily rehydrating from MariaDB if cold.
     */
    public synchronized Folio getFolio(String folioId) {
        Folio f = folios.get(folioId);
        if (f == null && DatabaseConnection.isDatabaseAvailable()) {
            f = folioDao.findFolioById(folioId);
            if (f != null) {
                folios.put(folioId, f);
            }
        }
        return f;
    }

    /**
     * Retrieves a SplitRoutingPair by booking ID, lazily rehydrating from MariaDB if cold.
     */
    public synchronized SplitRoutingPair getPairByBooking(int bookingId) {
        SplitRoutingPair pair = routingPairsByBooking.get(bookingId);
        if (pair == null && DatabaseConnection.isDatabaseAvailable()) {
            List<Folio> list = folioDao.findFoliosByBookingId(bookingId);
            if (!list.isEmpty()) {
                String masterId = "FOLIO-MASTER-" + bookingId;
                String guestId = "FOLIO-GUEST-" + bookingId;
                for (Folio item : list) {
                    folios.put(item.getFolioId(), item);
                    if (item.getType() == FolioType.MASTER) {
                        masterId = item.getFolioId();
                    } else if (item.getType() == FolioType.GUEST) {
                        guestId = item.getFolioId();
                    }
                }
                pair = new SplitRoutingPair(masterId, guestId, bookingId);
                routingPairsByBooking.put(bookingId, pair);
                routingPairsByMaster.put(masterId, pair);
                routingPairsByGuest.put(guestId, pair);
            }
        }
        return pair;
    }

    /**
     * Cancels folios associated with a cancelled booking in strict compliance with ADR-06 (Append-Only Ledger).
     * <p>
     * Immutability Rule: balance == sum(charges) - sum(payments).
     * We NEVER force 'UPDATE folios SET balance = 0.00' directly!
     * If the folio has an outstanding balance (> 0) and paid_amount == 0,
     * we insert an offsetting reversal credit transaction (amount = -balance) into folio_transactions.
     * The resulting sum of charges mathematically cancels to 0.00, preserving audit trail and accounting reconciliation.
     * If the folio has balance == 0, status transitions to CANCELLED without redundant line items.
     */
    public synchronized void cancelBookingFolios(int bookingId) {
        SplitRoutingPair pair = getPairByBooking(bookingId);
        if (pair != null) {
            cancelSingleFolio(pair.getMasterFolioId());
            cancelSingleFolio(pair.getGuestFolioId());
        }
        if (DatabaseConnection.isDatabaseAvailable()) {
            try {
                folioDao.cancelFoliosByBookingId(bookingId);
            } catch (Exception ignored) {}
        }
    }

    private void cancelSingleFolio(String folioId) {
        Folio folio = getFolio(folioId);
        if (folio != null && folio.getPaidAmount() == 0.0) {
            double currentBalance = folio.getBalance();
            if (currentBalance > 0.0) {
                // ADR-06 Append-Only Ledger: Append offsetting reversal credit transaction
                double reversalAmount = -currentBalance;
                ChargeItem reversal = new ChargeItem(
                        "REV-" + chargeSeq.incrementAndGet(),
                        ChargeCategory.ROOM_RATE,
                        "Booking Cancellation - Full Ledger Reversal",
                        reversalAmount,
                        folioId,
                        quanlykhachsan.backend.system.SystemStateHolder.getCurrentBusinessDate().toString(),
                        Instant.now().toString(),
                        null
                );
                folio.addCharge(reversal); // Adds reversal charge: balance mathematically becomes 0.00
                if (DatabaseConnection.isDatabaseAvailable()) {
                    try {
                        folioDao.addTransaction(reversal);
                    } catch (Exception ignored) {}
                }
            }
            folio.status = FolioStatus.CANCELLED;
            if (DatabaseConnection.isDatabaseAvailable()) {
                try {
                    folioDao.updateFolioBalanceAndPayment(folioId, folio.getBalance(), folio.getPaidAmount(), folio.getStatus());
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void reset() {
        folios.clear();
        routingPairsByMaster.clear();
        routingPairsByGuest.clear();
        routingPairsByBooking.clear();
        groupMasterToGuests.clear();
    }
}
