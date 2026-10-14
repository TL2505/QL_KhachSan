package quanlykhachsan.frontend.api;

import quanlykhachsan.frontend.utils.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import quanlykhachsan.frontend.utils.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.room.Room;
import quanlykhachsan.backend.room.RoomType;

public class RoomAPI {

    private static final Gson gson = JsonUtil.getGson();

    /** Lấy toàn bộ danh sách phòng */
    public static List<Room> getAllRooms() {
        List<Room> rooms = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/v1/rooms");
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res == null || !res.has("status") || !"success".equals(res.get("status").getAsString()))
                throw new IllegalStateException("Room API returned an unsuccessful response");
            JsonArray data = res.getAsJsonArray("data");
            if (data != null) for (JsonElement el : data) rooms.add(gson.fromJson(el, Room.class));
            return rooms;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load rooms: " + e.getMessage(), e);
        }
    }

    /**
     * Thêm phòng mới.
     * @return "Success" nếu thành công, thông báo lỗi nếu thất bại
     */
    public static String addRoom(Room room) {
        try {
            String body = gson.toJson(room);
            String json = HttpUtil.sendPost("/v1/rooms", body);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return "Success";
            } else {
                return res != null ? res.get("message").getAsString() : "Loi khong xac dinh";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Loi ket noi: " + e.getMessage();
        }
    }

    /**
     * Xóa phòng theo ID.
     * @return "Success" nếu thành công, thông báo lỗi nếu thất bại
     */
    public static String deleteRoom(int roomId) {
        try {
            String json = HttpUtil.sendDelete("/v1/rooms/" + roomId);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return "Success";
            } else {
                return res != null ? res.get("message").getAsString() : "Loi khong xac dinh";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Loi ket noi: " + e.getMessage();
        }
    }

    /**
     * Cap nhat trang thai phong (available / booked / occupied / cleaning / maintenance).
     * @return "Success" neu thanh cong, thong bao loi neu that bai
     */
    public static String updateRoomStatus(int roomId, String newStatus) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("status", newStatus);
            if(!newStatus.equals("vacant_clean") && !newStatus.equals("occupied_clean"))return "Use the booking or maintenance workflow";
            String json = HttpUtil.sendPut("/v1/rooms/" + roomId + "/housekeeping", gson.toJson(body));
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return "Success";
            } else {
                return res != null ? res.get("message").getAsString() : "Loi khong xac dinh";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Loi ket noi: " + e.getMessage();
        }
    }

    /** Lấy chi tiết loại phòng theo ID */
    public static RoomType getRoomType(int typeId) {
        try {
            String json = HttpUtil.sendGet("/room-types/" + typeId);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                return gson.fromJson(res.get("data"), RoomType.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Tìm phòng trống theo ngày */
    public static List<Room> searchAvailableRooms(String checkIn, String checkOut) {
        List<Room> rooms = new ArrayList<>();
        try {
            String json = HttpUtil.sendGet("/rooms/search?checkIn=" + checkIn + "&checkOut=" + checkOut);
            JsonObject res = gson.fromJson(json, JsonObject.class);
            if (res != null && "success".equals(res.get("status").getAsString())) {
                JsonArray data = res.getAsJsonArray("data");
                if (data != null) {
                    for (JsonElement el : data) {
                        rooms.add(gson.fromJson(el, Room.class));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rooms;
    }
}
