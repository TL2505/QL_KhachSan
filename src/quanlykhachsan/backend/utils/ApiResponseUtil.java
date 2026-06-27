package quanlykhachsan.backend.utils;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ApiResponseUtil {
    private static final Gson gson = JsonUtil.getGson();

    public static <T> String success(String message, T data) {
        return gson.toJson(new ApiResponse<>("success", message, data));
    }

    public static String success(String message) {
        return gson.toJson(new ApiResponse<>("success", message));
    }

    public static <T> String successWithData(T data) {
        return gson.toJson(new ApiResponse<>("success", null, data));
    }

    public static String error(String message) {
        return gson.toJson(new ApiResponse<>("error", message, null));
    }

    public static void write(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
