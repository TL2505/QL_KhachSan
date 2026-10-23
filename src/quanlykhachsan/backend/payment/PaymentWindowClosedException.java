package quanlykhachsan.backend.payment;

/** The guest's original QR window ended; the pending booking remains available for counter payment. */
public final class PaymentWindowClosedException extends RuntimeException {
    public PaymentWindowClosedException() { super("QR_PAYMENT_WINDOW_CLOSED"); }
}
