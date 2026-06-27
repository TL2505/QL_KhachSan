package quanlykhachsan.backend.hotelservice;

import com.google.gson.Gson;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.hotelservice.ServiceUsageDAOImpl;
import quanlykhachsan.backend.hotelservice.ServiceUsage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ServiceUsageController implements HttpHandler {
    private final ServiceUsageDAOImpl usageDAO = new ServiceUsageDAOImpl();
    private final Gson gson = JsonUtil.getGson();

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
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(usageDAO.getUsageByBookingId(bookingId)));
                } else {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Missing bookingId parameter"));
                }
            } else if ("POST".equals(method)) {
                ServiceUsage u = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), ServiceUsage.class);
                usageDAO.addServiceUsage(u);
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.success("Service added to booking"));
            } else if ("DELETE".equals(method)) {
                String[] parts = path.split("/");
                if (parts.length > 3) {
                    int id = Integer.parseInt(parts[3]);
                    usageDAO.deleteServiceUsage(id);
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Service usage removed"));
                } else {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Missing ID"));
                }
            } else {
                ApiResponseUtil.write(exchange, 405, ApiResponseUtil.error("Method not allowed"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Internal server error"));
        }
    }

}
