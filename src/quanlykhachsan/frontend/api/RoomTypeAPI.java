package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.RoomType;

public class RoomTypeAPI {
    public static List<RoomType> getAllRoomTypes() {
        List<RoomType> roomTypes = new ArrayList<>();
        try {
            String jsonResponse = HttpUtil.sendGet("/room-types");
            Gson gson = new Gson();
            JsonObject resObj = gson.fromJson(jsonResponse, JsonObject.class);
            if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
                JsonArray dataArray = resObj.getAsJsonArray("data");
                if (dataArray != null) {
                    for (JsonElement element : dataArray) {
                        RoomType rt = gson.fromJson(element, RoomType.class);
                        roomTypes.add(rt);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return roomTypes;
    }
}
