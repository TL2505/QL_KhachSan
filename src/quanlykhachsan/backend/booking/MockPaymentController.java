package quanlykhachsan.backend.booking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import quanlykhachsan.backend.payment.PaymentReconciliationService;
import quanlykhachsan.backend.payment.PaymentStatus;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Simulates a successful payment only while the explicitly selected local mock gateway is active. */
public final class MockPaymentController implements HttpHandler {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            ApiResponseUtil.write(exchange, 405, ApiResponseUtil.error("Method not allowed"));
            return;
        }
        if (!"mock".equals(System.getenv().getOrDefault("PAYMENT_QR_PROVIDER", "payos"))) {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Mock payment is disabled"));
            return;
        }

        String[] path = exchange.getRequestURI().getPath().split("/");
        if (path.length != 6 || !"mock-payment".equals(path[5])) {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Not found"));
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            FolioAccess.State folio = FolioAccess.find(connection, exchange, path[4]);
            if (folio == null) {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Payment reference not found"));
                return;
            }

            Link link = findMockLink(connection, folio.id());
            if (link == null || link.paymentLinkId() == null) {
                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("No mock payment link exists for this folio"));
                return;
            }
            if ("PAID".equals(link.state())) {
                JsonObject result = new JsonObject();
                result.addProperty("folio_id", folio.id());
                result.addProperty("balance", folio.balance());
                for (var entry : PaymentStatus.forFolio(connection, folio.id(), folio.balance()).entrySet()) {
                    result.add(entry.getKey(), entry.getValue());
                }
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(result));
                return;
            }
            if (!"PENDING".equals(link.state())
                    || !"open".equals(folio.status())
                    || folio.balance().signum() <= 0
                    || link.amount().compareTo(folio.balance()) != 0
                    || !link.expiresAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Mock payment link is no longer payable"));
                return;
            }

            var payment = new PaymentReconciliationService.VerifiedPayment(
                    "mock", "MOCK-" + link.orderCode(), link.orderCode(), link.amount(), "VND",
                    link.account(), link.reference(), link.paymentLinkId(),
                    LocalDateTime.now(BUSINESS_ZONE), "00");
            String response = new PaymentReconciliationService().settle(payment);
            JsonObject payload = JsonParser.parseString(response).getAsJsonObject();
            JsonObject data = payload.getAsJsonObject("data");
            if (data != null && "PAID".equals(data.get("payment_status").getAsString())) {
                markPaid(link.orderCode());
            }
            ApiResponseUtil.write(exchange, 200, response);
        } catch (SecurityException ex) {
            ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Role or guest identity is not authorized"));
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Invalid mock payment reference"));
        } catch (SQLException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Mock payment is temporarily unavailable"));
        } catch (Exception ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Mock payment could not be completed"));
        }
    }

    private static Link findMockLink(Connection connection, long folioId) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT order_code,payment_link_id,amount,account_number,reference_code,expires_at,state "
                        + "FROM payment_links WHERE folio_id=? AND provider='mock' ORDER BY order_code DESC LIMIT 1")) {
            query.setLong(1, folioId);
            try (var result = query.executeQuery()) {
                return result.next() ? new Link(result.getLong(1), result.getString(2), result.getBigDecimal(3),
                        result.getString(4), result.getString(5), result.getObject(6, LocalDateTime.class),
                        result.getString(7)) : null;
            }
        }
    }

    private static void markPaid(long orderCode) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             var query = connection.prepareStatement(
                     "UPDATE payment_links SET state='PAID' WHERE order_code=? AND provider='mock' AND state='PENDING'")) {
            query.setLong(1, orderCode);
            query.executeUpdate();
        }
    }

    private record Link(long orderCode, String paymentLinkId, BigDecimal amount, String account,
                        String reference, LocalDateTime expiresAt, String state) { }
}
