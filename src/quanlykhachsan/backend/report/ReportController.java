package quanlykhachsan.backend.report;

import quanlykhachsan.backend.report.ReportDAO;
import quanlykhachsan.backend.report.ReportDAOImpl;
import quanlykhachsan.backend.report.MonthlyRevenue;
import quanlykhachsan.backend.report.DailyStats;
import quanlykhachsan.backend.report.DashboardData;
import quanlykhachsan.backend.report.DashboardFilter;
import quanlykhachsan.backend.utils.SecurityUtil;
import java.io.InputStreamReader;
import java.io.Reader;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.ApiResponseUtil;
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
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Endpoint không tồn tại"));
        }
    }

    private void handleGetMonthlyRevenue(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;

        List<MonthlyRevenue> data = reportDAO.getMonthlyRevenue();
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
    }

    private void handleGetTodayStats(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2))
            return;

        DailyStats stats = reportDAO.getDailyStats();
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(stats));
    }

    private void handleGetActiveAccounts(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;

        int activeCount = reportDAO.getActiveAccountCount();
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(activeCount));
    }

    private void handleGetDashboardData(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange))
            return;

        try (Reader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            DashboardFilter filter = gson.fromJson(reader, DashboardFilter.class);
            DashboardData data = reportDAO.getDashboardData(filter);
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi xử lý dữ liệu Dashboard"));
        }
    }
}
