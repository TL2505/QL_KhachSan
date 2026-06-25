package quanlykhachsan.backend.hotelservice;

import com.google.gson.Gson;
import quanlykhachsan.backend.utils.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.hotelservice.ServiceDAOImpl;
import quanlykhachsan.backend.hotelservice.Service;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ServiceController implements HttpHandler {
    private final ServiceDAOImpl serviceDAO = new ServiceDAOImpl();
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        String method = exchange.getRequestMethod();

        try {
            if ("GET".equals(method)) {
                String response = gson.toJson(serviceDAO.selectAllServices());
                sendResponse(exchange, 200, response);
            } else if ("POST".equals(method)) {
                Service s = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), Service.class);
                serviceDAO.addService(s);
                sendResponse(exchange, 201, "{\"message\": \"Service created successfully\"}");
            } else if ("PUT".equals(method)) {
                Service s = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), Service.class);
                serviceDAO.updateService(s);
                sendResponse(exchange, 200, "{\"message\": \"Service updated successfully\"}");
            } else if ("DELETE".equals(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length > 3) {
                    int id = Integer.parseInt(parts[3]);
                    serviceDAO.deleteService(id);
                    sendResponse(exchange, 200, "{\"message\": \"Service deleted successfully\"}");
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
