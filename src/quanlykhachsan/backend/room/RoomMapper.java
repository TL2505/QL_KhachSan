package quanlykhachsan.backend.room;

import quanlykhachsan.backend.room.dto.RoomCreateRequest;
import quanlykhachsan.backend.room.dto.RoomResponse;

public class RoomMapper {
    public static Room toRoom(RoomCreateRequest request) {
        Room room = new Room();
        room.setRoomNumber(request.getRoomNumber());
        room.setRoomTypeId(request.getRoomTypeId() != null ? request.getRoomTypeId() : 1);
        room.setPrice(request.getPrice() != null ? request.getPrice() : 0.0);
        room.setStatus(request.getStatus() != null ? request.getStatus() : "available");
        return room;
    }

    public static RoomResponse toRoomResponse(Room room) {
        RoomResponse response = new RoomResponse();
        response.setId(room.getId());
        response.setRoomNumber(room.getRoomNumber());
        response.setRoomTypeId(room.getRoomTypeId());
        response.setPrice(room.getPrice());
        response.setStatus(room.getStatus());
        return response;
    }
}
