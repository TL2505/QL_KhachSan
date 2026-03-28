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

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Gson gson = new Gson();

        // LOGGING REQUEST (Giúp Debug trên Terminal của bạn)
        System.out.println("----------------------------------------");
        System.out.println("[" + method + "] Path: " + path);

        try {
            // 1. GET /api/rooms
            if ("GET".equalsIgnoreCase(method)) {
                List<Room> rooms = roomService.getAllRooms();
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(rooms));
                sendResponse(exchange, 200, gson.toJson(resObj));
            } 
            // 2. POST /api/rooms
            else if ("POST".equalsIgnoreCase(method)) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Room newRoom = gson.fromJson(body, Room.class);
                boolean success = roomService.addRoom(newRoom);
                if (success) {
                    sendSuccess(exchange, 201, "Thêm phòng thành công");
                } else {
                    sendError(exchange, 500, "Lỗi khi thêm phòng");
                }
            }
            // 3. PUT (Update ID-based)
            else if ("PUT".equalsIgnoreCase(method)) {
                int roomId = extractRoomId(path);
                System.out.println("[DEBUG] Extracted Room ID: " + roomId);

                if (roomId == -1) {
                    sendError(exchange, 400, "URL không hợp lệ, không tìm thấy Room ID");
                    return;
                }

                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject reqObj = gson.fromJson(body, JsonObject.class);

                if (path.contains("/status")) {
                    String newStatus = reqObj.get("status").getAsString();
                    System.out.println("[DEBUG] Updating logic: STATUS -> " + newStatus);
                    boolean success = roomService.updateRoomStatus(roomId, newStatus);
                    if (success) {
                        sendSuccess(exchange, 200, "Cập nhật trạng thái thành công");
                    } else {
                        sendError(exchange, 404, "Không tìm thấy phòng số " + roomId);
                    }
                } else {
                    System.out.println("[DEBUG] Updating logic: FULL INFO");
                    Room roomToUpdate = gson.fromJson(reqObj, Room.class);
                    roomToUpdate.setId(roomId);
                    boolean success = roomService.updateRoom(roomToUpdate);
                    if (success) {
                        sendSuccess(exchange, 200, "Cập nhật thông tin phòng thành công");
                    } else {
                        sendError(exchange, 404, "Không tìm thấy phòng!");
                    }
                }
            }
            // 4. DELETE
            else if ("DELETE".equalsIgnoreCase(method)) {
                int roomId = extractRoomId(path);
                System.out.println("[DEBUG] Deleting Room ID: " + roomId);
                if (roomId != -1) {
                    boolean success = roomService.deleteRoom(roomId);
                    if (success) sendSuccess(exchange, 200, "Xóa phòng thành công");
                    else sendError(exchange, 404, "Không tìm thấy phòng");
                } else {
                    sendError(exchange, 400, "Thiếu Room ID");
                }
            }
            else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Request failed: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Lỗi Server Room: " + e.getMessage());
        }
    }

    // Hàm tách ID thông minh từ Path (Tự động tìm ID đứng sau "rooms")
    private int extractRoomId(String path) {
        try {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length; i++) {
                if ("rooms".equals(parts[i]) && i + 1 < parts.length) {
                    // ID nằm ngay sau phầm tử "rooms"
                    return Integer.parseInt(parts[i+1]);
                }
            }
            // Nếu không tìm thấy patterns (e.g. DELETE /api/rooms/10), lấy phần tử cuối cùng nếu nó là số
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private void sendSuccess(HttpExchange exchange, int code, String msg) throws IOException {
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.addProperty("message", msg);
        sendResponse(exchange, code, new Gson().toJson(res));
    }

    private void sendError(HttpExchange exchange, int code, String msg) throws IOException {
        JsonObject res = new JsonObject();
        res.addProperty("status", "error");
        res.addProperty("message", msg);
        sendResponse(exchange, code, new Gson().toJson(res));
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
