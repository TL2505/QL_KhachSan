package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Customer;

public class CustomerAPI {

    public static List<Customer> getAllCustomers() {
        List<Customer> customers = new ArrayList<>();
        try {
            String jsonResponse = HttpUtil.sendGet("/customers");
            Gson gson = new Gson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);

            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray dataArray = resObj.getAsJsonArray("data");
                if (dataArray != null) {
                    for (JsonElement element : dataArray) {
                        Customer customer = gson.fromJson(element, Customer.class);
                        customers.add(customer);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return customers;
    }

    public static String addCustomer(Customer customer) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("name", customer.getFullName());
            req.addProperty("phone", customer.getPhone());
            req.addProperty("cccd", customer.getIdentityCard());

            String jsonResponse = HttpUtil.sendPost("/customers", new Gson().toJson(req));
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

    public static String updateCustomer(Customer customer) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("name", customer.getFullName());
            req.addProperty("phone", customer.getPhone());
            req.addProperty("cccd", customer.getIdentityCard());

            String jsonResponse = HttpUtil.sendPut("/customers/" + customer.getId(), new Gson().toJson(req));
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

    public static String deleteCustomer(int id) {
        try {
            String jsonResponse = HttpUtil.sendDelete("/customers/" + id);
            Gson gson = new Gson();
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
}
