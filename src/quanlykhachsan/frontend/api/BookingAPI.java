package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import quanlykhachsan.frontend.utils.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.booking.Booking;

public class BookingAPI {

    public static JsonObject bookRoom(int customerId, int roomId, String checkInDate, String checkOutDate) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("customerId", customerId);
            req.addProperty("roomId", roomId);
            req.addProperty("checkInDate", checkInDate);
            req.addProperty("checkOutDate", checkOutDate);

            String jsonResponse = HttpUtil.sendPost("/bookings", JsonUtil.getGson().toJson(req));
            return JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String checkIn(int bookingId) {
        try {
            String jsonResponse = HttpUtil.sendPut("/bookings/checkin/" + bookingId, "");
            JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
            if (resObj != null) {
                return ("success".equals(resObj.get("status").getAsString()) ? "Success: " : "Error: ") 
                        + resObj.get("message").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Exception: " + e.getMessage();
        }
        return "Unknown Error";
    }

    public static String checkOut(int bookingId) {
        try {
            String jsonResponse = HttpUtil.sendPut("/bookings/checkout/" + bookingId, "");
            JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
            if (resObj != null) {
                return ("success".equals(resObj.get("status").getAsString()) ? "Success: " : "Error: ") 
                        + resObj.get("message").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Exception: " + e.getMessage();
        }
        return "Unknown Error";
    }

    public static List<Booking> getAllBookings() {
        List<Booking> bookings = new ArrayList<>();
        try {
            String jsonResponse = HttpUtil.sendGet("/bookings");
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray dataArray = resObj.getAsJsonArray("data");
                if (dataArray != null) {
                    for (JsonElement element : dataArray) {
                        Booking b = gson.fromJson(element, Booking.class);
                        bookings.add(b);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bookings;
    }

    public static List<Booking> getBookingsByCustomer(int customerId) {
        List<Booking> bookings = new ArrayList<>();
        try {
            String jsonResponse = HttpUtil.sendGet("/bookings/customer/" + customerId);
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray dataArray = resObj.getAsJsonArray("data");
                if (dataArray != null) {
                    for (JsonElement element : dataArray) {
                        Booking b = gson.fromJson(element, Booking.class);
                        bookings.add(b);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bookings;
    }

    public static Booking getActiveBookingByRoom(int roomId) {
        try {
            String jsonResponse = HttpUtil.sendGet("/bookings/room/" + roomId);
            Gson gson = JsonUtil.getGson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                if (resObj.get("data").isJsonNull()) return null;
                return gson.fromJson(resObj.get("data"), Booking.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean processPayment(int bookingId, int customerId, double amount, String paymentMethod) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("bookingId", bookingId);
            req.addProperty("customerId", customerId);
            req.addProperty("amount", amount);
            req.addProperty("paymentMethod", paymentMethod);
            String jsonResponse = HttpUtil.sendPost("/payments", JsonUtil.getGson().toJson(req));
            JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
            if (resObj != null) {
                return "success".equals(resObj.get("status").getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
