package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RoomController implements HttpHandler {

    private RoomService roomService = new RoomService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Xử lý API: GET /api/rooms (Lấy danh sách phòng)
        if ("GET".equalsIgnoreCase(method)) {
            
            // 1. Gọi Service lấy danh sách phòng từ Database
            List<Room> rooms = roomService.getAllRooms();

            // 2. Chuyển đổi List<Room> thành chuỗi JSON (Thủ công vì chưa dùng Gson)
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\n");
            jsonBuilder.append("  \"status\": \"success\",\n");
            jsonBuilder.append("  \"data\": [\n");

            for (int i = 0; i < rooms.size(); i++) {
                Room room = rooms.get(i);
                jsonBuilder.append("    {\n");
                jsonBuilder.append("      \"id\": ").append(room.getId()).append(",\n");
                jsonBuilder.append("      \"roomNumber\": \"").append(room.getRoomNumber()).append("\",\n");
                jsonBuilder.append("      \"roomTypeId\": ").append(room.getRoomTypeId()).append(",\n");
                jsonBuilder.append("      \"price\": ").append(room.getPrice()).append(",\n");
                jsonBuilder.append("      \"status\": \"").append(room.getStatus()).append("\"\n");
                jsonBuilder.append("    }");
                
                // Nếu chưa phải phần tử cuối cùng thì thêm dấu phẩy
                if (i < rooms.size() - 1) {
                    jsonBuilder.append(",");
                }
                jsonBuilder.append("\n");
            }
            
            jsonBuilder.append("  ]\n");
            jsonBuilder.append("}");

            // 3. Gửi Response về cho Frontend
            String responseStr = jsonBuilder.toString();
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
