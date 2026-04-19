package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import quanlykhachsan.frontend.utils.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.LoyaltyHistory;

public class LoyaltyAPI {

    public static List<Customer> getAllLoyaltyCustomers() {
        List<Customer> customers = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/loyalty/customers");
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(json, JsonObject.class);
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray arr = resObj.getAsJsonArray("data");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        Customer c = gson.fromJson(el, Customer.class);
                        customers.add(c);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return customers;
    }

    public static List<LoyaltyHistory> getHistory(int customerId) {
        List<LoyaltyHistory> list = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/loyalty/history/" + customerId);
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(json, JsonObject.class);
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray arr = resObj.getAsJsonArray("data");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        LoyaltyHistory h = gson.fromJson(el, LoyaltyHistory.class);
                        list.add(h);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean redeemPoints(int customerId, int pointsToRedeem, double discountAmount, String description) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("customerId", customerId);
            req.addProperty("pointsToRedeem", pointsToRedeem);
            req.addProperty("discountAmount", discountAmount);
            req.addProperty("description", description);

            String json = HttpUtil.sendPost("/loyalty/redeem", JsonUtil.getGson().toJson(req));
            JsonObject resObj = JsonUtil.getGson().fromJson(json, JsonObject.class);
            return resObj != null && "success".equals(resObj.get("status").getAsString());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Customer getCustomerLoyalty(int customerId) {
        try {
            String json = HttpUtil.sendGet("/loyalty/customers"); // We can filter here or add a specific endpoint
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(json, JsonObject.class);
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray arr = resObj.getAsJsonArray("data");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        Customer c = gson.fromJson(el, Customer.class);
                        if (c.getId() == customerId) return c;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
