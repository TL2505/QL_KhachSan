package quanlykhachsan.backend.room;

import quanlykhachsan.backend.room.Room;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.room.RoomService;
import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import quanlykhachsan.backend.report.DailyStats;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.room.RoomType;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.room.dto.RoomCreateRequest;
import quanlykhachsan.backend.room.dto.RoomStatusUpdateRequest;
import quanlykhachsan.backend.room.dto.RoomResponse;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RoomController implements HttpHandler {

    private RoomService roomService = new RoomService();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Cho phép CORS
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath(); // /api/rooms hoặc /api/rooms/123

        // Lấy phần sau /api/rooms
        String pathInfo = path.replace("/api/rooms", "");

        if ("GET".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            // GET /api/rooms - Lấy danh sách tất cả phòng
            handleGetAll(exchange);
        } else if ("GET".equalsIgnoreCase(method) && (pathInfo.equals("/search") || path.endsWith("/search"))) {
            // GET /api/rooms/search - Tìm phòng trống theo ngày
            handleSearch(exchange);
        } else if ("POST".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            // POST /api/rooms - Thêm phòng mới
            handleAdd(exchange);

        } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+/status")) {
            // PUT /api/rooms/{id}/status - Đổi trạng thái phòng
            int id = Integer.parseInt(pathInfo.split("/")[1]);
            handleUpdateStatus(exchange, id);

        } else if ("DELETE".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
            // DELETE /api/rooms/{id} - Xóa phòng theo ID
            int id = Integer.parseInt(pathInfo.substring(1));
            handleDelete(exchange, id);

        } else if ("GET".equalsIgnoreCase(method) && path.equals("/api/room-types")) {
            // GET /api/room-types - Lấy danh sách loại phòng
            handleGetAllRoomTypes(exchange);

        } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/room-types/")) {
            // GET /api/room-types/{id} - Lấy chi tiết loại phòng
            int typeId = Integer.parseInt(path.substring("/api/room-types/".length()));
            handleGetRoomTypeById(exchange, typeId);

        } else {
            ApiResponseUtil.write(exchange, 405, ApiResponseUtil.error("Phương thức không được hỗ trợ"));
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        List<Room> rooms = roomService.getAllRooms();
        List<RoomResponse> dtoList = new java.util.ArrayList<>();
        for (Room r : rooms) {
            dtoList.add(RoomMapper.toRoomResponse(r));
        }
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(dtoList));
    }

    private void handleUpdateStatus(HttpExchange exchange, int roomId) throws IOException {
        // Cập nhật trạng thái phòng (thường dùng cho lễ tân check-in/out)
        // Không yêu cầu quyền Admin ở đây để nhân viên có thể thao tác
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            RoomStatusUpdateRequest req = gson.fromJson(body, RoomStatusUpdateRequest.class);
            String newStatus = req.getStatus();

            if (newStatus == null || newStatus.trim().isEmpty()) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Trạng thái không hợp lệ"));
                return;
            }

            boolean ok = roomService.updateRoomStatus(roomId, newStatus.trim());
            if (ok) {
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Cập nhật trạng thái thành công"));
            } else {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy phòng với ID: " + roomId));
            }
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleAdd(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            RoomCreateRequest req = gson.fromJson(body, RoomCreateRequest.class);
            Room room = RoomMapper.toRoom(req);

            if (room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Số phòng không được để trống"));
                return;
            }
            if (room.getPrice() <= 0) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Giá phòng phải lớn hơn 0"));
                return;
            }
            if (room.getStatus() == null || room.getStatus().isEmpty()) {
                room.setStatus("available");
            }

            boolean ok = roomService.addRoom(room);
            if (ok) {
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.success("Thêm phòng thành công"));
            } else {
                ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Thêm phòng thất bại"));
            }
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleDelete(HttpExchange exchange, int roomId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        boolean ok = roomService.deleteRoom(roomId);
        if (ok) {
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Xóa phòng thành công"));
        } else {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy phòng với ID: " + roomId));
        }
    }


    private void handleGetAllRoomTypes(HttpExchange exchange) throws IOException {
        List<RoomType> list = roomService.getAllRoomTypes();
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(list));
    }

    private void handleGetRoomTypeById(HttpExchange exchange, int id) throws IOException {
        RoomType rt = roomService.getRoomTypeById(id);
        if (rt != null) {
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(rt));
        } else {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy loại phòng"));
        }
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query == null) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu thông tin tìm kiếm"));
                return;
            }

            String checkInStr = null, checkOutStr = null;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length < 2) continue;
                if ("checkIn".equals(pair[0])) checkInStr = pair[1];
                if ("checkOut".equals(pair[0])) checkOutStr = pair[1];
            }

            if (checkInStr == null || checkOutStr == null) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu ngày nhận hoặc trả phòng"));
                return;
            }

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.util.Date checkIn = sdf.parse(checkInStr);
            java.util.Date checkOut = sdf.parse(checkOutStr);

            List<Room> availableRooms = roomService.findAvailableRooms(checkIn, checkOut);
            List<RoomResponse> dtoList = new java.util.ArrayList<>();
            for (Room r : availableRooms) {
                dtoList.add(RoomMapper.toRoomResponse(r));
            }
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(dtoList));
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi tìm kiếm: " + e.getMessage()));
        }
    }
}
