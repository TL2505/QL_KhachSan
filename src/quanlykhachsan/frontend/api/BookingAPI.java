package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Booking;

public class BookingAPI {

    public static List<Booking> getAllBookings() {
        List<Booking> bookings = new ArrayList<>();
        try {
            String jsonResponse = HttpUtil.sendGet("/bookings");
            Gson gson = new Gson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray data = resObj.getAsJsonArray("data");
                for (JsonElement e : data) {
                    bookings.add(gson.fromJson(e, Booking.class));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return bookings;
    }

    public static JsonObject bookRoom(int customerId, int roomId, String checkInDate, String checkOutDate) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("customerId", customerId);
            req.addProperty("roomId", roomId);
            req.addProperty("checkInDate", checkInDate);
            req.addProperty("checkOutDate", checkOutDate);

            String jsonResponse = HttpUtil.sendPost("/bookings", new Gson().toJson(req));
            return new Gson().fromJson(jsonResponse, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", e.getMessage());
            return err;
        }
    }

    public static String checkIn(int bookingId) {
        try {
            String jsonResponse = HttpUtil.sendPut("/bookings/checkin/" + bookingId, "");
            JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
            if (resObj != null) {
                return ("success".equals(resObj.get("status").getAsString()) ? "Success: " : "Error: ") 
                        + (resObj.has("message") ? resObj.get("message").getAsString() : "Check-in done");
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
            JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
            if (resObj != null) {
                return ("success".equals(resObj.get("status").getAsString()) ? "Success: " : "Error: ") 
                        + (resObj.has("message") ? resObj.get("message").getAsString() : "Check-out done");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Exception: " + e.getMessage();
        }
        return "Unknown Error";
    }
}
