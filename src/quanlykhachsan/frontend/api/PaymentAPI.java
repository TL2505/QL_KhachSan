package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.frontend.utils.JsonUtil;

public class PaymentAPI {

    public static String pay(int bookingId, double amount, String paymentMethod, int customerId) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("bookingId", bookingId);
            req.addProperty("amount", amount);
            req.addProperty("paymentMethod", paymentMethod);
            req.addProperty("customerId", customerId);

            String jsonResponse = HttpUtil.sendPost("/payments", JsonUtil.getGson().toJson(req));
            JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);

            if (resObj != null) {
                String status = resObj.has("status") && !resObj.get("status").isJsonNull() ? resObj.get("status").getAsString() : "error";
                String message = null;
                try {
                    if (resObj.has("message") && !resObj.get("message").isJsonNull()) message = resObj.get("message").getAsString();
                } catch (Exception ignored) {}
                if (message == null && resObj.has("data") && resObj.get("data").isJsonObject()) {
                    JsonObject inner = resObj.getAsJsonObject("data");
                    try {
                        if (inner.has("message") && !inner.get("message").isJsonNull()) message = inner.get("message").getAsString();
                    } catch (Exception ignored) {}
                }

                if (message == null) message = "(no message)";

                if ("success".equalsIgnoreCase(status)) {
                    return "Success: " + message;
                } else {
                    return "Error: " + message;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Exception: " + e.getMessage();
        }
        return "Unknown Error";
    }

    public static boolean processPayment(int bookingId, double amount, String paymentMethod) {
        String res = pay(bookingId, amount, paymentMethod, -1);
        return res.startsWith("Success");
    }
}
