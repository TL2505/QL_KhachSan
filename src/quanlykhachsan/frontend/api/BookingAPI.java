package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class BookingAPI {

    public static String bookRoom(int customerId, int roomId, String checkInDate, String checkOutDate) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("customerId", customerId);
            req.addProperty("roomId", roomId);
            req.addProperty("checkInDate", checkInDate);
            req.addProperty("checkOutDate", checkOutDate);

            String jsonResponse = HttpUtil.sendPost("/bookings", new Gson().toJson(req));
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

    public static String checkIn(int bookingId) {
        try {
            String jsonResponse = HttpUtil.sendPut("/bookings/checkin/" + bookingId, "");
            JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
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
            JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
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
}
