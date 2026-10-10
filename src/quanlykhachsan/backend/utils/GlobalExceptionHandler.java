package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class GlobalExceptionHandler extends Filter {

    @Override
    public String description() {
        return "Global Exception Handler and Standard Error Formatter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        try {
            chain.doFilter(exchange);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "ERR_400_BAD_REQUEST", e.getMessage());
        } catch (SecurityException e) {
            sendError(exchange, 403, "ERR_403_FORBIDDEN", e.getMessage());
        } catch (IllegalStateException e) {
            sendError(exchange, 409, "ERR_409_CONFLICT", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "ERR_500_SERVER_ERROR", "Internal Server Error: " + e.getMessage());
        }
    }

    public static void sendError(HttpExchange exchange, int statusCode, String errorCode, String message) throws IOException {
        // Prevent sending error if response headers were already sent
        if (exchange.getResponseHeaders().containsKey("Content-Type") && exchange.getResponseCode() != -1) {
            return; 
        }

        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                                            .withZone(ZoneOffset.UTC)
                                            .format(Instant.now());
        
        String safeMessage = message != null ? message.replace("\"", "\\\"") : "Unknown Error";
        
        String jsonResponse = String.format(
            "{\"error_code\": \"%s\", \"message\": \"%s\", \"timestamp\": \"%s\"}",
            errorCode, safeMessage, timestamp
        );

        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
