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
}
