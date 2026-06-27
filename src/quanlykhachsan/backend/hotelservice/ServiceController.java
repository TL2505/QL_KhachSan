package quanlykhachsan.backend.hotelservice;

import com.google.gson.Gson;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.hotelservice.ServiceDAOImpl;
import quanlykhachsan.backend.hotelservice.Service;
import quanlykhachsan.backend.hotelservice.dto.ServiceCreateRequest;
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
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(serviceDAO.selectAllServices()));
            } else if ("POST".equals(method)) {
                ServiceCreateRequest req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), ServiceCreateRequest.class);
                Service s = new Service();
                s.setName(req.getName());
                s.setDescription(req.getDescription());
                s.setPrice(req.getPrice());
                serviceDAO.addService(s);
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.success("Service created successfully"));
            } else if ("PUT".equals(method)) {
                ServiceCreateRequest req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), ServiceCreateRequest.class);
                Service s = new Service();
                s.setName(req.getName());
                s.setDescription(req.getDescription());
                s.setPrice(req.getPrice());
                serviceDAO.updateService(s);
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Service updated successfully"));
            } else if ("DELETE".equals(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length > 3) {
                    int id = Integer.parseInt(parts[3]);
                    serviceDAO.deleteService(id);
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Service deleted successfully"));
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
