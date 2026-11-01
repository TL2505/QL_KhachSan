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
        collectionMethod(request.paymentMethod());
        if(request.amount()==null||request.amount().signum()<0||request.amount().scale()>2||request.amount().compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("Invalid collection amount");
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
                if(request.checkout()&&!java.util.Set.of("checked_in","checked_out").contains(folio.bookingStatus())) {
                    c.rollback(); return PaymentResult.error(409,"ERR_409_INVALID_STATE","Only checked-in bookings may check out");
                }
                CheckoutAdjustmentService.CheckoutPlan checkoutPlan = null;
                if(request.checkout()&&"checked_in".equals(folio.bookingStatus())) {
                    try { checkoutPlan = new CheckoutAdjustmentService().assess(c,folio.bookingId()); }
                    catch(CheckoutAdjustmentService.CheckoutAdjustmentException ex) {
                        c.rollback(); return PaymentResult.error(409,"ERR_409_CHECKOUT_RATE_REQUIRED",ex.getMessage());
                    }
                }
                BigDecimal currentFolioBalance=dao.folioBalance(c,request.folioId());
                if (request.amount().compareTo(currentFolioBalance) > 0) { c.rollback(); return PaymentResult.error(409, "ERR_409_CONFLICT", "Payment exceeds the folio balance"); }
                BigDecimal balance = FolioLedgerMath.apply(currentFolioBalance, "credit", request.amount());
                if (request.amount().signum() > 0) {
                    dao.insertPaymentCredit(c, request);
                    injector.afterLedgerInsert();
                }
                dao.updateBalance(c, request.folioId(), balance);
                if(request.amount().signum()>0){
                    String method=collectionMethod(request.paymentMethod());
                    quanlykhachsan.backend.payment.PaymentReconciliationService.recordCollection(c,method.equals("cash")?"cash":"pos",method,request.idempotencyKey(),request.folioId(),request.amount(),balance);
                    quanlykhachsan.backend.notification.LifecycleNotifications.payment(c,folio.bookingId(),request.idempotencyKey(),request.amount(),balance);
                }
                FolioDao.confirmPaidBooking(c,folio.bookingId());
                boolean checkedOut = "checked_out".equals(folio.bookingStatus());
                if(request.checkout()){
                    if("checked_in".equals(folio.bookingStatus())) {
                        if(dao.bookingBaseBalance(c,folio.bookingId()).signum()!=0){
                            c.rollback();return PaymentResult.error(409,"ERR_409_UNPAID_FOLIOS","Unpaid base folio balances prevent checkout; payment was not recorded");
                        }
                        checkedOut=dao.checkout(c,folio.bookingId(),folio.roomId(),checkoutPlan.rentalType(),checkoutPlan.bufferMinutes());
                        if(!checkedOut){c.rollback();return PaymentResult.error(409,"ERR_409_INVALID_STATE","Only checked-in bookings may check out");}
                    }
                }
                BigDecimal bookingBalance=dao.bookingBalance(c,folio.bookingId());
                if("checked_out".equals(folio.bookingStatus())&&bookingBalance.signum()==0)
                    quanlykhachsan.backend.invoice.InvoiceFinalizationService.finalizePaid(c,folio.bookingId());
                JsonObject data = new JsonObject();
                data.addProperty("folio_id", request.folioId()); data.addProperty("booking_id", folio.bookingId());
                data.addProperty("balance", balance); data.addProperty("checked_out", checkedOut);
                if(request.checkout()) {
                    data.addProperty("booking_balance",bookingBalance);
                    data.addProperty("checkout_required_payment",bookingBalance.signum()>0);
                    if(bookingBalance.signum()>0)data.addProperty("checkout_message",checkedOut
                            ?"Phòng đã được trả; vui lòng thu tiếp số dư phụ thu trên folio."
                            :"Đã ghi nhận khoản thu. Vui lòng thanh toán các khoản còn lại để hoàn tất trả phòng.");
                }
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
    public static String collectionMethod(String value){
        String normalized=value==null?"":value.trim().toLowerCase(java.util.Locale.ROOT);
        if(java.util.Set.of("cash","tiền mặt (cash)").contains(normalized))return "cash";
        if(java.util.Set.of("pos","credit_card","visa","mastercard","credit card","thẻ tín dụng (credit card)").contains(normalized))return "POS";
        throw new IllegalArgumentException("Use an authorized cash/POS collection; bank transfers require a verified webhook");
    }
    public record PaymentResult(int statusCode, String body) {
        static PaymentResult error(int status, String code, String message) {
            JsonObject error = new JsonObject(); error.addProperty("error_code", code);
            error.addProperty("message", message); error.addProperty("timestamp", java.time.Instant.now().toString());
            return new PaymentResult(status, quanlykhachsan.backend.utils.JsonUtil.getGson().toJson(error));
        }
    }
}
