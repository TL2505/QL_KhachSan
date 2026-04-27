package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.dao.ReportDAO;
import quanlykhachsan.backend.daoimpl.ReportDAOImpl;
import quanlykhachsan.backend.model.MonthlyRevenue;
import quanlykhachsan.backend.model.DailyStats;
import quanlykhachsan.backend.model.DashboardData;
import quanlykhachsan.backend.model.DashboardFilter;
import quanlykhachsan.backend.utils.SecurityUtil;
import java.io.InputStreamReader;
import java.io.Reader;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;

public class ReportController implements HttpHandler {

    private ReportDAO reportDAO = new ReportDAOImpl();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("📥 Nhận yêu cầu: " + exchange.getRequestMethod() + " tại " + exchange.getRequestURI());

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method) && path.equals("/api/reports/monthly-revenue")) {
            handleGetMonthlyRevenue(exchange);
        } else if ("GET".equalsIgnoreCase(method) && path.equals("/api/reports/today-stats")) {
            handleGetTodayStats(exchange);
        } else if ("GET".equalsIgnoreCase(method) && path.equals("/api/reports/active-accounts")) {
            handleGetActiveAccounts(exchange);
        } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/reports/dashboard")) {
            handleGetDashboardData(exchange);
        } else {
            sendJson(exchange, 404, buildError("Endpoint không tồn tại"));
        }
    }

    private void handleGetMonthlyRevenue(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;

        List<MonthlyRevenue> data = reportDAO.getMonthlyRevenue();
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(data));
        sendJson(exchange, 200, res.toString());
    }

    private void handleGetTodayStats(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2))
            return;

        DailyStats stats = reportDAO.getDailyStats();
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(stats));
        sendJson(exchange, 200, res.toString());
    }

    private void handleGetActiveAccounts(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;

        int activeCount = reportDAO.getActiveAccountCount();
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.addProperty("data", activeCount);
        sendJson(exchange, 200, res.toString());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleGetDashboardData(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;

        try (Reader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            DashboardFilter filter = gson.fromJson(reader, DashboardFilter.class);
            DashboardData data = reportDAO.getDashboardData(filter);
            
            JsonObject res = new JsonObject();
            res.addProperty("status", "success");
            res.add("data", gson.toJsonTree(data));
            sendJson(exchange, 200, res.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, buildError("Lỗi xử lý dữ liệu Dashboard"));
        }
    }

    private String buildError(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "error");
        obj.addProperty("message", msg);
        return gson.toJson(obj);
    }
}
