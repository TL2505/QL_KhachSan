package quanlykhachsan.frontend.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import quanlykhachsan.frontend.utils.HttpUtil;

/** Desktop API for folio balances and idempotent folio payments. */
public final class FolioAPI {
    private FolioAPI() { }

    public static List<FolioBalance> getForBooking(int bookingId) throws Exception {
        String response = HttpUtil.sendGet("/v1/folios/booking/" + bookingId);
        JsonObject envelope = JsonParser.parseString(response).getAsJsonObject();
        if (!"success".equalsIgnoreCase(envelope.get("status").getAsString())) {
            throw new IllegalStateException("Folio balances could not be loaded");
        }
        JsonArray rows = envelope.getAsJsonArray("data");
        List<FolioBalance> result = new ArrayList<>();
        if (rows != null) {
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                result.add(new FolioBalance(row.get("id").getAsInt(),
                    row.get("folio_type").getAsString(), row.get("balance").getAsBigDecimal()));
            }
        }
        return result;
    }

    public static HttpUtil.HttpResult pay(int folioId, BigDecimal amount, String paymentMethod,
                                          boolean checkout, String idempotencyKey) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("amount", amount);
        request.addProperty("payment_method", paymentMethod);
        request.addProperty("checkout", checkout);
        return HttpUtil.sendPostWithHeaders("/v1/folios/" + folioId + "/pay",
            request.toString(), Map.of("Idempotency-Key", idempotencyKey));
    }

    public static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public static final class FolioBalance {
        private final int id;
        private final String type;
        private final BigDecimal balance;

        public FolioBalance(int id, String type, BigDecimal balance) {
            this.id = id;
            this.type = type;
            this.balance = balance == null ? BigDecimal.ZERO : balance;
        }

        public int getId() { return id; }
        public String getType() { return type; }
        public BigDecimal getBalance() { return balance; }
    }
}
