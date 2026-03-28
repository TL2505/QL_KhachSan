package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Room;

public class RoomAPI {

    public static List<Room> getAllRooms() {
        List<Room> rooms = new ArrayList<>();
        try {
            String jsonResponse = HttpUtil.sendGet("/rooms");
            Gson gson = new Gson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray dataArray = resObj.getAsJsonArray("data");
                if (dataArray != null) {
                    for (JsonElement element : dataArray) {
                        Room room = gson.fromJson(element, Room.class);
                        rooms.add(room);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rooms;
    }

    public static String updateRoomStatus(int roomId, String status) {
        try {
            Gson gson = new Gson();
            JsonObject reqObj = new JsonObject();
            reqObj.addProperty("status", status);
            String jsonResponse = HttpUtil.sendPut("/rooms/" + roomId + "/status", gson.toJson(reqObj));
            
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            return resObj.has("message") ? resObj.get("message").getAsString() : "Success";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public static String addRoom(Room room) {
        try {
            Gson gson = new Gson();
            String jsonResponse = HttpUtil.sendPost("/rooms", gson.toJson(room));
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            return resObj.has("message") ? resObj.get("message").getAsString() : "Success";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public static String updateRoom(Room room) {
        try {
            Gson gson = new Gson();
            String jsonResponse = HttpUtil.sendPut("/rooms/" + room.getId(), gson.toJson(room));
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            return resObj.has("message") ? resObj.get("message").getAsString() : "Success";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public static String deleteRoom(int roomId) {
        try {
            String jsonResponse = HttpUtil.sendDelete("/rooms/" + roomId);
            Gson gson = new Gson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            return resObj.has("message") ? resObj.get("message").getAsString() : "Success";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}
