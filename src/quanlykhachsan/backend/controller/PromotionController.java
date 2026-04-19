package quanlykhachsan.backend.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.model.Promotion;
import quanlykhachsan.backend.service.PromotionService;
import quanlykhachsan.backend.service.BookingService;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.daoimpl.CustomerDAOImpl;
import quanlykhachsan.backend.daoimpl.RoomDAOImpl;
import quanlykhachsan.backend.utils.SecurityUtil;
import java.net.URI;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Arrays;

public class PromotionController implements HttpHandler {

    private PromotionService promotionService = new PromotionService();
    private BookingService bookingService = new BookingService();
    private CustomerDAOImpl customerDAO = new CustomerDAOImpl();
    private RoomDAOImpl roomDAO = new RoomDAOImpl();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String pathInfo = path.replace("/api/promotions", "");

        if (!SecurityUtil.hasPermission(exchange, 1, 2, 3)) return;

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (pathInfo.isEmpty() || pathInfo.equals("/")) {
                    List<Promotion> list = promotionService.getAllPromotions();
                    sendJson(exchange, 200, buildSuccessWithData(list));
                } else if (pathInfo.equals("/active")) {
                    List<Promotion> list = promotionService.getActivePromotions();
                    sendJson(exchange, 200, buildSuccessWithData(list));
                } else if (pathInfo.startsWith("/best")) {
                    // Usage: /api/promotions/best?bookingId=5
                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> params = parseQuery(query);
                    if (params.containsKey("bookingId")) {
                        int bId = Integer.parseInt(params.get("bookingId"));
                        Booking b = bookingService.getBookingById(bId);
                        if (b != null) {
                            Customer c = customerDAO.findById(b.getCustomerId());
                            Room r = roomDAO.findById(b.getRoomId());
                            Promotion best = promotionService.calculateBestDiscount(b, c, r);
                            
                            // Return both promotion object and calculated discount value
                            JsonObject res = new JsonObject();
                            res.add("promotion", gson.toJsonTree(best));
                            
                            double discount = 0;
                            if (best != null) {
                                long diff = b.getCheckOutDate().getTime() - b.getCheckInDate().getTime();
                                long nights = Math.max(1, diff / (1000 * 60 * 60 * 24));
                                if ("percentage".equals(best.getDiscountType())) {
                                    discount = (r.getPrice() * nights) * (best.getDiscountValue() / 100.0);
                                } else {
                                    discount = best.getDiscountValue();
                                }
                            }
                            res.addProperty("calculatedDiscount", discount);
                            sendJson(exchange, 200, buildSuccessWithData(res));
                        } else {
                            sendJson(exchange, 404, buildError("Booking không tồn tại"));
                        }
                    } else {
                        sendJson(exchange, 400, buildError("Thiếu bookingId"));
                    }
                } else if (pathInfo.startsWith("/preview")) {
                    // Usage: /api/promotions/preview?roomId=1&customerId=2&checkIn=2024-01-01&checkOut=2024-01-02
                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> p = parseQuery(query);
                    if (p.containsKey("roomId") && p.containsKey("customerId")) {
                        try {
                            int rId = Integer.parseInt(p.get("roomId"));
                            int cId = Integer.parseInt(p.get("customerId"));
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                            java.util.Date in = sdf.parse(p.get("checkIn"));
                            java.util.Date out = sdf.parse(p.get("checkOut"));

                            Booking b = new Booking();
                            b.setRoomId(rId);
                            b.setCustomerId(cId);
                            b.setCheckInDate(in);
                            b.setCheckOutDate(out);

                            Customer c = customerDAO.findById(cId);
                            Room r = roomDAO.findById(rId);
                            Promotion best = promotionService.calculateBestDiscount(b, c, r);

                            JsonObject res = new JsonObject();
                            res.add("promotion", gson.toJsonTree(best));
                            double discount = 0;
                            if (best != null) {
                                long nights = Math.max(1, (out.getTime() - in.getTime()) / (1000 * 60 * 60 * 24));
                                if ("percentage".equals(best.getDiscountType())) {
                                    discount = (r.getPrice() * nights) * (best.getDiscountValue() / 100.0);
                                } else {
                                    discount = best.getDiscountValue();
                                }
                            }
                            res.addProperty("calculatedDiscount", discount);
                            sendJson(exchange, 200, buildSuccessWithData(res));
                        } catch (Exception ex) {
                            sendJson(exchange, 400, buildError("Thông số không hợp lệ hoặc sai định dạng ngày (yyyy-MM-dd)"));
                        }
                    } else {
                        sendJson(exchange, 400, buildError("Thiếu roomId hoặc customerId"));
                    }
                } else if (pathInfo.matches("/\\d+")) {
                    int id = Integer.parseInt(pathInfo.substring(1));
                    Promotion p = promotionService.getPromotionById(id);
                    if (p != null) {
                        sendJson(exchange, 200, buildSuccessWithData(p));
                    } else {
                        sendJson(exchange, 404, buildError("Không tìm thấy"));
                    }
                }
            } else if ("POST".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
                if (!SecurityUtil.hasPermission(exchange, 1)) return;
                
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Promotion p = gson.fromJson(body, Promotion.class);
                
                if (promotionService.createPromotion(p)) {
                    sendJson(exchange, 201, buildSuccess("Thêm mới thành công"));
                } else {
                    sendJson(exchange, 400, buildError("Lỗi cấu hình (trùng lắp hoặc ngày không hợp lệ)"));
                }
            } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
                if (!SecurityUtil.hasPermission(exchange, 1)) return;

                int id = Integer.parseInt(pathInfo.substring(1));
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Promotion p = gson.fromJson(body, Promotion.class);
                p.setId(id);
                
                if (promotionService.updatePromotion(p)) {
                    sendJson(exchange, 200, buildSuccess("Cập nhật thành công"));
                } else {
                    sendJson(exchange, 400, buildError("Lỗi cập nhật dữ liệu"));
                }
            } else if ("DELETE".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
                if (!SecurityUtil.hasPermission(exchange, 1)) return;

                int id = Integer.parseInt(pathInfo.substring(1));
                if (promotionService.deletePromotion(id)) {
                    sendJson(exchange, 200, buildSuccess("Xóa thành công"));
                } else {
                    sendJson(exchange, 400, buildError("Lỗi xóa dữ liệu"));
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, buildError(e.getMessage()));
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    
    private String buildSuccess(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "success");
        obj.addProperty("message", msg);
        return gson.toJson(obj);
    }
    
    private String buildSuccessWithData(Object data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "success");
        obj.add("data", gson.toJsonTree(data));
        return gson.toJson(obj);
    }
    
    private String buildError(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "error");
        obj.addProperty("message", msg);
        return gson.toJson(obj);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }
        }
        return result;
    }
}
