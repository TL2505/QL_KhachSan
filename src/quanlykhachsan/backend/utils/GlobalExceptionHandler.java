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
        try { chain.doFilter(exchange); } catch (Exception e) { handle(exchange, e); }
    }

    public static void handle(HttpExchange exchange, Throwable exception) throws IOException {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof IllegalArgumentException) sendError(exchange,400,"ERR_400_BAD_REQUEST",cause.getMessage());
        else if (cause instanceof SecurityException) sendError(exchange,403,"ERR_403_FORBIDDEN",cause.getMessage());
        else if (cause instanceof IllegalStateException || cause instanceof OptimisticLockException) sendError(exchange,409,"ERR_409_CONFLICT",cause.getMessage());
        else sendError(exchange,500,"ERR_500_SERVER_ERROR","The request could not be completed.");
    }

    public static void sendError(HttpExchange exchange, int statusCode, String errorCode, String message) throws IOException {
        // Prevent sending error if response headers were already sent
        if (exchange.getResponseHeaders().containsKey("Content-Type") && exchange.getResponseCode() != -1) {
            return; 
        }

        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                                            .withZone(ZoneOffset.UTC)
                                            .format(Instant.now());
        
        com.google.gson.JsonObject error = new com.google.gson.JsonObject();
        error.addProperty("error_code", errorCode);
        error.addProperty("message", message == null ? "Unknown Error" : message);
        error.addProperty("timestamp", timestamp);
        String jsonResponse = error.toString();

        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
