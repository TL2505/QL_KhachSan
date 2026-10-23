package quanlykhachsan.backend.payment;

import com.google.gson.JsonObject;

/** Provider adapters create QR links; only verified server events enter settlement. */
public interface PaymentGateway {
    String provider();
    record Beneficiary(String account,String holder,String bin) { }
    Beneficiary beneficiary();
    void requireConfigured();
    JsonObject createLink(long orderCode,long amount,String reference,long expiresAt)throws Exception;

    /** Close an unpaid link. Returns the provider's authoritative state (for example CANCELLED or PAID). */
    default String cancelLink(long orderCode,String reason)throws Exception { return "UNSUPPORTED"; }
}
