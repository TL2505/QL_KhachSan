package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.dao.ReportDAO;
import quanlykhachsan.backend.daoimpl.ReportDAOImpl;
import quanlykhachsan.backend.model.MonthlyRevenue;
import quanlykhachsan.backend.utils.SecurityUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class ReportController implements HttpHandler {

    private ReportDAO reportDAO = new ReportDAOImpl();
    private Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("📥 Nhận yêu cầu: " + exchange.getRequestMethod() + " tại " + exchange.getRequestURI());

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method) && path.equals("/api/reports/monthly-revenue")) {
            handleGetMonthlyRevenue(exchange);
        } else {
            sendJson(exchange, 404, buildError("Endpoint không tồn tại"));
        }
    }

    private void handleGetMonthlyRevenue(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        
        List<MonthlyRevenue> data = reportDAO.getMonthlyRevenue();
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(data));
        sendJson(exchange, 200, res.toString());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildError(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "error");
        obj.addProperty("message", msg);
        return gson.toJson(obj);
    }
}
