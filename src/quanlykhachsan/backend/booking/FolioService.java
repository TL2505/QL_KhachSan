package quanlykhachsan.backend.booking;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Coordinates atomic folio ledger writes and idempotent payment replay. */
public final class FolioService {
    private final FolioDao dao;

    public FolioService() { this(new FolioDao()); }
    public FolioService(FolioDao dao) { this.dao = Objects.requireNonNull(dao); }

    public PaymentResult pay(Connection c, FolioDao.PaymentRequest request) throws SQLException {
        return pay(c, request, () -> { });
    }

    /** Test seam runs after ledger insertion and before balance mutation. */
    public PaymentResult pay(Connection c, FolioDao.PaymentRequest request, FailureInjector injector) throws SQLException {
        dao.acquireLock(c, request.idempotencyKey());
        try {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                FolioDao.PaymentReplay replay = dao.findReplay(c, request.idempotencyKey());
                if (replay != null) {
                    c.rollback();
                    if (!replay.requestHash().equals(request.requestHash())) return PaymentResult.error(409, "ERR_409_CONFLICT", "Idempotency-Key was used with a different request");
                    return new PaymentResult(replay.statusCode(), replay.responseBody());
                }
                dao.reserveIdempotency(c, request.idempotencyKey(), request.requestHash());
                FolioDao.FolioState folio = dao.lockFolio(c, request.folioId());
                if (folio == null) { c.rollback(); return PaymentResult.error(404, "ERR_404_NOT_FOUND", "Folio not found"); }
                if (request.amount().compareTo(folio.balance()) > 0) { c.rollback(); return PaymentResult.error(409, "ERR_409_CONFLICT", "Payment exceeds the folio balance"); }
                BigDecimal balance = FolioLedgerMath.apply(folio.balance(), "credit", request.amount());
                if (request.amount().signum() > 0) {
                    dao.insertPaymentCredit(c, request);
                    injector.afterLedgerInsert();
                }
                dao.updateBalance(c, request.folioId(), balance);
                boolean checkedOut = false;
                if (request.checkout() && new FolioRoutingEngine().canCloseBooking(c, folio.bookingId()))
                    checkedOut = dao.checkout(c, folio.bookingId(), folio.roomId());
                JsonObject data = new JsonObject();
                data.addProperty("folio_id", request.folioId()); data.addProperty("booking_id", folio.bookingId());
                data.addProperty("balance", balance); data.addProperty("checked_out", checkedOut);
                JsonObject envelope = new JsonObject(); envelope.addProperty("status", "success");
                envelope.addProperty("message", "Payment recorded"); envelope.add("data", data);
                String body = quanlykhachsan.backend.utils.JsonUtil.getGson().toJson(envelope);
                dao.saveReplay(c, request.idempotencyKey(), body);
                c.commit();
                return new PaymentResult(200, body);
            } catch (SQLException | RuntimeException ex) {
                c.rollback(); throw ex;
            } finally { c.setAutoCommit(true); }
        } finally { dao.releaseLock(c, request.idempotencyKey()); }
    }

    @FunctionalInterface public interface FailureInjector { void afterLedgerInsert() throws SQLException; }
    public record PaymentResult(int statusCode, String body) {
        static PaymentResult error(int status, String code, String message) {
            JsonObject error = new JsonObject(); error.addProperty("error_code", code);
            error.addProperty("message", message); error.addProperty("timestamp", java.time.Instant.now().toString());
            return new PaymentResult(status, quanlykhachsan.backend.utils.JsonUtil.getGson().toJson(error));
        }
    }
}
