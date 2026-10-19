package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import quanlykhachsan.frontend.utils.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.customer.Customer;

public class CustomerAPI {

    public static List<Customer> getAllCustomers() {
        List<Customer> customers = new ArrayList<>();
        try {
            String jsonResponse = HttpUtil.sendGet("/v1/customers");
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);

            if (resObj == null || !resObj.has("status") || !"success".equals(resObj.get("status").getAsString()))
                throw new IllegalStateException("Customer API returned an unsuccessful response");
            JsonArray dataArray = resObj.getAsJsonArray("data");
            if (dataArray != null) for (JsonElement element : dataArray) customers.add(gson.fromJson(element, Customer.class));
            return customers;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load customers: " + e.getMessage(), e);
        }
    }

    public static String addCustomer(Customer customer) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("name", customer.getFullName());
            req.addProperty("phone", customer.getPhone());
            req.addProperty("cccd", customer.getIdentityCard());

            String jsonResponse = HttpUtil.sendPost("/v1/customers", JsonUtil.getGson().toJson(req));
            JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
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

    public static String updateCustomer(Customer customer) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("name", customer.getFullName());
            req.addProperty("phone", customer.getPhone());
            req.addProperty("cccd", customer.getIdentityCard());

            String jsonResponse = HttpUtil.sendPut("/v1/customers/" + customer.getId(), JsonUtil.getGson().toJson(req));
            JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
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

    public static String deleteCustomer(int id) {
        try {
            String jsonResponse = HttpUtil.sendDelete("/v1/customers/" + id);
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
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

    public static Customer getCustomerById(int id) {
        try {
            String jsonResponse = HttpUtil.sendGet("/v1/customers/" + id);
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                return gson.fromJson(resObj.get("data"), Customer.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
