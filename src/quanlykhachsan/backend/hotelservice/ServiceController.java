package quanlykhachsan.backend.hotelservice;

import com.google.gson.Gson;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.hotelservice.dto.ServiceCreateRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ServiceController implements HttpHandler {
    private final ServiceDAOImpl serviceDAO = new ServiceDAOImpl();
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        String method = exchange.getRequestMethod();

        try {
            if("PUT".equals(method)&&exchange.getRequestURI().getPath().matches("/api/v1/services/[0-9]+/category")){
                if(!quanlykhachsan.backend.utils.SecurityUtil.hasPermission(exchange,1,2))return;
                var request=quanlykhachsan.backend.utils.PortalValidation.body(exchange);
                String category=quanlykhachsan.backend.utils.PortalValidation.text(request,"category",20,true);
                if(!java.util.Set.of("FNB","SPA","HOUSEKEEPING").contains(category))throw new IllegalArgumentException("Invalid category");
                int id=Integer.parseInt(exchange.getRequestURI().getPath().split("/")[4]);
                try(var c=quanlykhachsan.backend.utils.DatabaseConnection.getConnection();var q=c.prepareStatement("UPDATE services SET category=? WHERE id=?")){
                    q.setString(1,category);q.setInt(2,id);if(q.executeUpdate()!=1){ApiResponseUtil.sendError(exchange,404,"ERR_404_NOT_FOUND","Service not found");return;}
                }
                ApiResponseUtil.sendSuccess(exchange,java.util.Map.of("id",id,"category",category));return;
            }
            if(exchange.getRequestURI().getPath().equals("/api/v1/services/catalog")&&"GET".equals(method)){
                var rows=new com.google.gson.JsonArray();
                try(var c=quanlykhachsan.backend.utils.DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT id,name,description,price,category FROM services WHERE status='active' AND category IS NOT NULL ORDER BY category,id");var r=q.executeQuery()){
                    while(r.next()){
                        var row=new com.google.gson.JsonObject();row.addProperty("id",r.getInt(1));row.addProperty("name",r.getString(2));row.addProperty("description",r.getString(3));row.addProperty("price",r.getBigDecimal(4));row.addProperty("category",r.getString(5));rows.add(row);
                    }
                }
                ApiResponseUtil.sendSuccess(exchange,rows);return;
            }
            if(!"GET".equals(method)&&!quanlykhachsan.backend.utils.SecurityUtil.hasPermission(exchange,1,2))return;
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
        } catch (IllegalArgumentException e) {
            ApiResponseUtil.sendError(exchange,400,"ERR_400_BAD_REQUEST","Invalid service request");
        } catch (java.sql.SQLException e) {
            ApiResponseUtil.sendError(exchange,503,"ERR_503_UNAVAILABLE","Service catalog is unavailable");
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Internal server error"));
        }
    }

}
