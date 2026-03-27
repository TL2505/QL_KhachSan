package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
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

        // Xử lý API: GET /api/rooms (Lấy danh sách phòng)
        if ("GET".equalsIgnoreCase(method)) {
            
            // 1. Gọi Service lấy danh sách phòng từ Database
            List<Room> rooms = roomService.getAllRooms();

            // 2. Chuyển đổi List<Room> thành chuỗi JSON bằng Gson
            Gson gson = new Gson();
            JsonObject resObj = new JsonObject();
            resObj.addProperty("status", "success");
            
            // Gson có thể tự động chuyển đổi danh sách object thành JsonArray
            resObj.add("data", gson.toJsonTree(rooms));
            
            String responseStr = gson.toJson(resObj);

            // 3. Gửi Response về cho Frontend
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // Tránh lỗi CORS
            
            byte[] responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();

        } else {
            // Nếu dùng phương thức khác (POST, PUT, DELETE) -> 405 Method Not Allowed
            exchange.sendResponseHeaders(405, -1);
        }
    }
}
