package quanlykhachsan.backend.controller;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.daoimpl.ServiceUsageDAOImpl;
import quanlykhachsan.backend.model.ServiceUsage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ServiceUsageController implements HttpHandler {
    private final ServiceUsageDAOImpl usageDAO = new ServiceUsageDAOImpl();
    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("GET".equals(method)) {
                // Example route: /api/service-usage?bookingId=1
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("bookingId=")) {
                    int bookingId = Integer.parseInt(query.split("=")[1]);
                    String response = gson.toJson(usageDAO.getUsageByBookingId(bookingId));
                    sendResponse(exchange, 200, response);
                } else {
                    sendResponse(exchange, 400, "{\"error\": \"Missing bookingId parameter\"}");
                }
            } else if ("POST".equals(method)) {
                ServiceUsage u = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), ServiceUsage.class);
                usageDAO.addServiceUsage(u);
                sendResponse(exchange, 201, "{\"message\": \"Service added to booking\"}");
            } else if ("DELETE".equals(method)) {
                String[] parts = path.split("/");
                if (parts.length > 3) {
                    int id = Integer.parseInt(parts[3]);
                    usageDAO.deleteServiceUsage(id);
                    sendResponse(exchange, 200, "{\"message\": \"Service usage removed\"}");
                } else {
                    sendResponse(exchange, 400, "{\"error\": \"Missing ID\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
