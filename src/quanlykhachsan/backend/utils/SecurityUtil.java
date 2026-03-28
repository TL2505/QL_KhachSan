package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SecurityUtil {

    /**
     * Checks if the request has one of the allowed roles.
     * If not, it sends a 403 Forbidden response.
     * 
     * @param exchange The HttpExchange object
     * @param allowedRoles Array of allowed role IDs
     * @return true if authorized, false otherwise
     */
    public static boolean hasPermission(HttpExchange exchange, int... allowedRoles) throws IOException {
        String roleHeader = exchange.getRequestHeaders().getFirst("X-User-Role");
        
        try {
            int roleId = (roleHeader != null) ? Integer.parseInt(roleHeader) : -1;
            for (int allowedRole : allowedRoles) {
                if (roleId == allowedRole) return true;
            }
        } catch (NumberFormatException e) {}

        sendError(exchange, 403, "Bạn không có quyền thực hiện hành động này.");
        return false;
    }

    /**
     * Convenience method for Admin only checks.
     */
    public static boolean checkAdmin(HttpExchange exchange) throws IOException {
        return hasPermission(exchange, 1);
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String json = String.format("{\"status\":\"error\",\"message\":\"%s\"}", message);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
