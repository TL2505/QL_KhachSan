package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class PaymentAPI {

    public static String pay(int bookingId, double amount, String paymentMethod, int customerId) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("bookingId", bookingId);
            req.addProperty("amount", amount);
            req.addProperty("paymentMethod", paymentMethod);
            req.addProperty("customerId", customerId);

            String jsonResponse = HttpUtil.sendPost("/payments", new Gson().toJson(req));
            JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
            
            if (resObj != null) {
                if ("success".equals(resObj.get("status").getAsString())) {
                    return "Success: " + resObj.get("message").getAsString();
                } else {
                    return "Error: " + resObj.get("message").getAsString();
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
