package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class RoomController implements HttpHandler {

    private RoomService roomService = new RoomService();
    private Gson gson = new Gson();

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

        } else if ("POST".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            // POST /api/rooms - Thêm phòng mới
            handleAdd(exchange);

        } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+/status")) {
            // PUT /api/rooms/{id}/status - Đổi trạng thái phòng
            int roomId = Integer.parseInt(pathInfo.split("/")[1]);
            handleUpdateStatus(exchange, roomId);

        } else if ("DELETE".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
            // DELETE /api/rooms/{id} - Xóa phòng theo ID
            int roomId = Integer.parseInt(pathInfo.substring(1));
            handleDelete(exchange, roomId);

        } else {
            sendJson(exchange, 405, buildError("Phương thức không được hỗ trợ"));
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        List<Room> rooms = roomService.getAllRooms();
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(rooms));
        sendJson(exchange, 200, res.toString());
    }

    private void handleUpdateStatus(HttpExchange exchange, int roomId) throws IOException {
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject reqObj = gson.fromJson(body, JsonObject.class);
            String newStatus = reqObj.has("status") ? reqObj.get("status").getAsString() : null;

            if (newStatus == null || newStatus.trim().isEmpty()) {
                sendJson(exchange, 400, buildError("Trạng thái không hợp lệ"));
                return;
            }

            boolean ok = roomService.updateRoomStatus(roomId, newStatus.trim());
            if (ok) {
                JsonObject res = new JsonObject();
                res.addProperty("status", "success");
                res.addProperty("message", "Cập nhật trạng thái thành công");
                sendJson(exchange, 200, res.toString());
            } else {
                sendJson(exchange, 404, buildError("Không tìm thấy phòng với ID: " + roomId));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleAdd(HttpExchange exchange) throws IOException {
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Room room = gson.fromJson(body, Room.class);

            if (room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) {
                sendJson(exchange, 400, buildError("Số phòng không được để trống"));
                return;
            }
            if (room.getPrice() <= 0) {
                sendJson(exchange, 400, buildError("Giá phòng phải lớn hơn 0"));
                return;
            }
            if (room.getStatus() == null || room.getStatus().isEmpty()) {
                room.setStatus("available");
            }

            boolean ok = roomService.addRoom(room);
            if (ok) {
                JsonObject res = new JsonObject();
                res.addProperty("status", "success");
                res.addProperty("message", "Thêm phòng thành công");
                sendJson(exchange, 201, res.toString());
            } else {
                sendJson(exchange, 500, buildError("Thêm phòng thất bại"));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleDelete(HttpExchange exchange, int roomId) throws IOException {
        boolean ok = roomService.deleteRoom(roomId);
        if (ok) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "success");
            res.addProperty("message", "Xóa phòng thành công");
            sendJson(exchange, 200, res.toString());
        } else {
            sendJson(exchange, 404, buildError("Không tìm thấy phòng với ID: " + roomId));
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildError(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "error");
        obj.addProperty("message", msg);
        return gson.toJson(obj);
    }
}
