package quanlykhachsan.backend.report;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminOverviewReportControllerTest {
    @Test
    void versionedOverviewRouteReturnsStatsOnlyToAdmins() throws Exception {
        AdminOverviewStats expected = new AdminOverviewStats();
        expected.setBusinessDate("2026-10-26");
        expected.setCollectionsToday(new BigDecimal("1500000.00"));
        expected.setTotalRooms(10);
        expected.setSellableRooms(8);
        expected.setOccupiedRooms(5);
        expected.setRoomsNeedCleaning(2);
        expected.setPendingBookings(3);
        expected.setArrivalsToday(2);
        expected.setDeparturesToday(1);
        expected.setCollectionsThisMonth(new BigDecimal("9000000.00"));
        expected.setRefundsThisMonth(new BigDecimal("450000.00"));
        expected.setRefundRate(new BigDecimal("5.00"));
        expected.setOutstandingManualRefundAmount(new BigDecimal("250000.00"));
        expected.setOutstandingManualRefundCount(1);
        expected.setBookingsCreatedThisMonth(20);
        expected.setCancelledBookingsThisMonth(2);
        expected.setCancellationRate(new BigDecimal("10.00"));
        expected.setRevenueByMonth(List.of(new MonthlyRevenue("2026-10", 5, 9000000, 1000000, 10000000)));

        ReportDAO dao = new ReportDAO() {
            @Override public List<MonthlyRevenue> getMonthlyRevenue() { return List.of(); }
            @Override public DailyStats getDailyStats() { return null; }
            @Override public AdminOverviewStats getAdminOverview() { return expected; }
            @Override public int getActiveAccountCount() { return 0; }
            @Override public DashboardData getDashboardData(DashboardFilter filter) { return null; }
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var context = server.createContext("/api/v1/reports", new ReportController(dao));
        context.getFilters().add(new JwtFilter());
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/reports/admin-overview";
            HttpClient client = HttpClient.newHttpClient();
            assertEquals(401, get(client, url, null).statusCode());
            assertEquals(403, get(client, url, JwtTokenUtil.issueToken(71, "qa-guest", "guest")).statusCode());

            HttpResponse<String> response = get(client, url, JwtTokenUtil.issueToken(72, "qa-admin", "admin"));
            assertEquals(200, response.statusCode(), response.body());
            JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
            assertEquals("2026-10-26", data.get("businessDate").getAsString());
            assertEquals(1500000.00, data.get("collectionsToday").getAsDouble());
            assertEquals(5, data.get("occupiedRooms").getAsInt());
            assertEquals(5.00, data.get("refundRate").getAsDouble());
            assertEquals(1, data.getAsJsonArray("revenueByMonth").size());
        } finally {
            server.stop(0);
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
