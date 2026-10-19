package quanlykhachsan.backend.room;

import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.math.BigDecimal;
import com.google.gson.Gson;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.room.dto.RoomCreateRequest;
import quanlykhachsan.backend.room.dto.RoomStatusUpdateRequest;
import quanlykhachsan.backend.room.dto.RoomResponse;

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
        if(path.matches("/api/v1/rooms/[0-9]+/housekeeping")){new HousekeepingController().handle(exchange);return;}
        if(path.matches("/api/v1/rooms/[0-9]+/maintenance")){new MaintenanceController().handle(exchange);return;}
        if(path.matches("/api/v1/rooms/[0-9]+/minibar")){new MinibarController().handle(exchange);return;}

        // Preserve the legacy alias while parsing the versioned Phase 1 route correctly.
        String prefix = path.startsWith("/api/v1/rooms") ? "/api/v1/rooms" : "/api/rooms";
        String pathInfo = path.startsWith(prefix) ? path.substring(prefix.length()) : path;

        if ("GET".equalsIgnoreCase(method) && pathInfo.equals("/types")) {
            handleGetAllRoomTypes(exchange);
        } else if ("GET".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            // GET /api/rooms - Lấy danh sách tất cả phòng
            handleGetAll(exchange);
        } else if ("GET".equalsIgnoreCase(method) && (pathInfo.equals("/search") || path.endsWith("/search"))) {
            // GET /api/rooms/search - Tìm phòng trống theo ngày
            handleSearch(exchange);
        } else if ("POST".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            // POST /api/rooms - Thêm phòng mới
            handleAdd(exchange);

        } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
            int id = Integer.parseInt(pathInfo.substring(1));
            handleUpdate(exchange, id);

        } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+/status")) {
            // PUT /api/rooms/{id}/status - Đổi trạng thái phòng
            int id = Integer.parseInt(pathInfo.split("/")[1]);
            handleUpdateStatus(exchange, id);

        } else if ("POST".equalsIgnoreCase(method) && pathInfo.matches("/\\d+/downgrade")) {
            int bookingId = Integer.parseInt(pathInfo.split("/")[1]);
            if (!SecurityUtil.hasPermission(exchange, 1)) return;
            handleDowngrade(exchange, bookingId);

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

    private void handleDowngrade(HttpExchange exchange, int bookingId) throws IOException {
        try {
            com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            int replacementRoomId = body.get("replacement_room_id").getAsInt();
            var result = new RoomDowngradeService().downgrade(bookingId, replacementRoomId, true);
            com.google.gson.JsonObject data = new com.google.gson.JsonObject();
            data.addProperty("booking_id", result.bookingId()); data.addProperty("room_id", result.roomId());
            data.addProperty("old_rate", result.oldRate()); data.addProperty("new_rate", result.newRate());
            data.addProperty("folio_credit", result.folioCredit());
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(ex.getMessage()));
        } catch (Exception ex) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Room downgrade failed"));
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        try {
            List<Room> rooms = roomService.getAllRooms();
            List<RoomResponse> dtoList = new java.util.ArrayList<>();
            for (Room r : rooms) {
                dtoList.add(RoomMapper.toRoomResponse(r));
            }
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(dtoList));
        } catch (IllegalStateException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể tải danh sách phòng lúc này."));
        }
    }

    private void handleUpdateStatus(HttpExchange exchange, int roomId) throws IOException {
        ApiResponseUtil.write(exchange,403,ApiResponseUtil.error("Use the authorized booking, housekeeping or maintenance workflow"));
    }

    private void legacyUpdateStatus(HttpExchange exchange, int roomId) throws IOException {
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
            RoomCreateRequest req = readRoomRequest(exchange);
            Room room = buildValidatedRoom(req, 0, true);
            boolean ok = roomService.addRoom(room);
            if (ok) {
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.success("Thêm phòng thành công"));
            } else {
                ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Thêm phòng thất bại"));
            }
        } catch (RoomConflictException ex) {
            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể thêm phòng lúc này."));
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Dữ liệu phòng không hợp lệ."));
        }
    }

    private void handleUpdate(HttpExchange exchange, int roomId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            RoomCreateRequest req = readRoomRequest(exchange);
            Room room = buildValidatedRoom(req, roomId, false);
            if (roomService.updateRoom(room)) {
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Cập nhật phòng thành công"));
            } else {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy phòng."));
            }
        } catch (RoomConflictException ex) {
            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể cập nhật phòng lúc này."));
        } catch (Exception ex) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Dữ liệu phòng không hợp lệ."));
        }
    }

    private RoomCreateRequest readRoomRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        RoomCreateRequest request = gson.fromJson(body, RoomCreateRequest.class);
        if (request == null) throw new IllegalArgumentException("Thiếu thông tin phòng.");
        return request;
    }

    private Room buildValidatedRoom(RoomCreateRequest request, int roomId, boolean creating) {
        String number = request.getRoomNumber() == null ? "" : request.getRoomNumber().trim();
        if (!number.matches("[A-Za-z0-9][A-Za-z0-9-]{0,19}")) {
            throw new IllegalArgumentException("Số phòng chỉ gồm chữ, số hoặc dấu gạch ngang (tối đa 20 ký tự).");
        }
        if (request.getRoomTypeId() == null || request.getRoomTypeId() < 1
                || roomService.getRoomTypeById(request.getRoomTypeId()) == null) {
            throw new IllegalArgumentException("Vui lòng chọn hạng phòng hợp lệ.");
        }
        Double price = request.getPrice();
        if (price == null || !Double.isFinite(price) || price <= 0
                || BigDecimal.valueOf(price).compareTo(new BigDecimal("99999999.99")) > 0
                || BigDecimal.valueOf(price).stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Giá phòng phải lớn hơn 0 và tối đa có 2 chữ số thập phân.");
        }
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber(number);
        room.setRoomTypeId(request.getRoomTypeId());
        room.setPrice(price);
        if (creating) {
            String status = request.getStatus();
            if (status == null || status.isBlank()) status = "vacant_dirty";
            if (!java.util.Set.of("vacant_clean", "vacant_dirty", "out_of_order", "out_of_service").contains(status)) {
                throw new IllegalArgumentException("Trạng thái ban đầu của phòng không hợp lệ.");
            }
            room.setStatus(status);
        }
        return room;
    }

    private void handleDelete(HttpExchange exchange, int roomId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            boolean ok = roomService.deleteRoom(roomId);
            if (ok) {
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Xóa phòng thành công"));
            } else {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy phòng."));
            }
        } catch (RoomConflictException ex) {
            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể xóa phòng lúc này."));
        }
    }


    private void handleGetAllRoomTypes(HttpExchange exchange) throws IOException {
        try {
            List<RoomType> list = roomService.getAllRoomTypes();
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(list));
        } catch (IllegalStateException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể tải hạng phòng lúc này."));
        }
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
