package quanlykhachsan.backend.room;

import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.Gson;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.room.dto.RoomCreateRequest;
import quanlykhachsan.backend.room.dto.RoomStatusUpdateRequest;
import quanlykhachsan.backend.room.dto.RoomResponse;

import quanlykhachsan.backend.utils.GlobalExceptionHandler;

public class RoomController implements HttpHandler {

    private RoomService roomService = new RoomService();
    private final RoomDAO roomDao = new RoomDAOImpl();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String method = exchange.getRequestMethod();

        // TASK-A2.1 Endpoints
        if ("/api/v1/rooms/availability".equals(path) || path.endsWith("/rooms/availability")) {
            if ("GET".equalsIgnoreCase(method)) {
                handleGetAvailability(exchange);
            } else {
                ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed. Expected GET.");
            }
            return;
        }

        if ("/api/v1/rooms".equals(path) || "/api/rooms".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                handleGetAll(exchange);
                return;
            } else if ("POST".equalsIgnoreCase(method)) {
                handleAdd(exchange);
                return;
            }
        }

        // Lấy phần sau /api/rooms hoặc /api/v1/rooms
        String pathInfo = path.replace("/api/v1/rooms", "").replace("/api/rooms", "");

        if ("GET".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            handleGetAll(exchange);
        } else if ("GET".equalsIgnoreCase(method) && (pathInfo.equals("/search") || path.endsWith("/search"))) {
            handleSearch(exchange);
        } else if ("POST".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            handleAdd(exchange);
        } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+/status")) {
            int id = Integer.parseInt(pathInfo.split("/")[1]);
            handleUpdateStatus(exchange, id);
        } else if ("DELETE".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
            int id = Integer.parseInt(pathInfo.substring(1));
            handleDelete(exchange, id);
        } else if ("GET".equalsIgnoreCase(method) && path.equals("/api/room-types")) {
            handleGetAllRoomTypes(exchange);
        } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/room-types/")) {
            int typeId = Integer.parseInt(path.substring("/api/room-types/".length()));
            handleGetRoomTypeById(exchange, typeId);
        } else {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Endpoint not found: " + path);
        }
    }

    /**
     * GET /api/v1/rooms - Returns room catalog with total room count metadata.
     */
    public void handleGetAll(HttpExchange exchange) throws IOException {
        try {
            List<java.util.Map<String, Object>> rooms = roomDao.getAllRooms();
            ApiResponseUtil.sendSuccess(exchange, 200, rooms, java.util.Map.of("total", rooms.size()));
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * GET /api/v1/rooms/availability - Queries dynamic availability for a stay window.
     */
    public void handleGetAvailability(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("check_in and check_out are required");
            }

            String checkIn = null;
            String checkOut = null;
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length < 2) continue;
                String key = pair[0].trim();
                String val = java.net.URLDecoder.decode(pair[1].trim(), StandardCharsets.UTF_8);
                if ("check_in".equalsIgnoreCase(key) || "checkIn".equalsIgnoreCase(key)) {
                    checkIn = val;
                } else if ("check_out".equalsIgnoreCase(key) || "checkOut".equalsIgnoreCase(key)) {
                    checkOut = val;
                }
            }

            if (checkIn == null || checkIn.isBlank() || checkOut == null || checkOut.isBlank()) {
                throw new IllegalArgumentException("check_in and check_out are required");
            }

            List<java.util.Map<String, Object>> available = roomDao.getAvailableRooms(checkIn, checkOut);
            ApiResponseUtil.sendSuccess(exchange, 200, available, java.util.Map.of("total", available.size()));
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
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

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(checkIn);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 14);
            checkIn = cal.getTime();

            cal.setTime(checkOut);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 12);
            checkOut = cal.getTime();


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
