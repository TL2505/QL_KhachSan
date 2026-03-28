package quanlykhachsan.backend.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.service.DashboardService;

public class DashboardController implements HttpHandler {

    private DashboardService dashboardService = new DashboardService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        Gson gson = new Gson();

        try {
            // GET /api/dashboard/stats
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, Object> stats = dashboardService.getDashboardStats();
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(stats));
                sendResponse(exchange, 200, gson.toJson(resObj));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Lỗi Server Dashboard: " + e.getMessage());
        }
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

    private void sendError(HttpExchange exchange, int code, String msg) throws IOException {
        JsonObject res = new JsonObject();
        res.addProperty("status", "error");
        res.addProperty("message", msg);
        sendResponse(exchange, code, new Gson().toJson(res));
    }
}
