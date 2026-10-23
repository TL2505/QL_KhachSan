package quanlykhachsan.backend.payment;

import com.google.gson.JsonObject;

/** Local-only payment adapter selected explicitly with PAYMENT_QR_PROVIDER=mock. */
public final class MockPaymentGateway implements PaymentGateway {
    private static final Beneficiary TEST_BENEFICIARY =
            new Beneficiary("000000000000", "AURELIA TEST PAYMENT", "MOCK");

    @Override public String provider() { return "mock"; }
    @Override public Beneficiary beneficiary() { return TEST_BENEFICIARY; }
    @Override public void requireConfigured() { }
    @Override public String cancelLink(long orderCode,String reason) { return "CANCELLED"; }

    @Override
    public JsonObject createLink(long orderCode, long amount, String reference, long expiresAt) {
        JsonObject data = new JsonObject();
        data.addProperty("paymentLinkId", "MOCK-" + orderCode);
        data.addProperty("qrCode", "AURELIA-MOCK|" + orderCode + "|" + amount + "|" + reference);
        data.addProperty("checkoutUrl", "");
        return data;
    }
}
