package quanlykhachsan.backend.booking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

/** Read folio summaries and settle folios using idempotent payment requests. */
public final class FolioController implements HttpHandler {
    private static final Pattern PAYMENT_PATH = Pattern.compile("/api/v1/folios/(\\d+)/pay");
    private static final Pattern SPLIT_PATH = Pattern.compile("/api/v1/folios/(\\d+)/split");
    private static final Pattern ROUTING_PATH = Pattern.compile("/api/v1/folios/(\\d+)/routing");
    private static final Pattern BOOKING_FOLIOS_PATH = Pattern.compile("/api/v1/folios/booking/(\\d+)");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if(path.matches("/api/v1/folios/[^/]+/vietqr")){new VietQrController().handle(exchange);return;}
        if(path.matches("/api/v1/folios/[^/]+/payment-status")){new PaymentPollController().handle(exchange);return;}
        try {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Matcher matcher = BOOKING_FOLIOS_PATH.matcher(path);
                if (!matcher.matches()) {
                    writeError(exchange, 404, "ERR_404_NOT_FOUND", "Folio route not found");
                    return;
                }
                if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
                writeFolioList(exchange, Integer.parseInt(matcher.group(1)));
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Matcher routing = ROUTING_PATH.matcher(path);
                    if (!routing.matches()) { writeError(exchange, 404, "ERR_404_NOT_FOUND", "Folio route not found"); return; }
                    if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
                    routeCharge(exchange, Long.parseLong(routing.group(1)));
                    return;
                }
                writeError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
                return;
            }
            if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
            Matcher split = SPLIT_PATH.matcher(path);
            if (split.matches()) { splitFolio(exchange, Long.parseLong(split.group(1))); return; }
            Matcher matcher = PAYMENT_PATH.matcher(path);
            if (!matcher.matches()) {
                writeError(exchange, 404, "ERR_404_NOT_FOUND", "Folio route not found");
                return;
            }
            settle(exchange, Long.parseLong(matcher.group(1)));
        } catch (IllegalArgumentException ex) {
            writeError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid folio request");
        } catch (Exception ex) {
            System.err.println("[FolioController] Request failed: " + ex.getClass().getSimpleName());
            if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                writeError(exchange, 500, "ERR_500_INTERNAL_SERVER_ERROR", "Unable to process folio request");
            }
        }
    }

    private void splitFolio(HttpExchange exchange, long folioId) throws Exception {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int bookingId = bookingId(c, folioId);
                long guestFolio = new FolioRoutingEngine().ensureGuestFolio(c, bookingId);
                c.commit();
                JsonObject result = new JsonObject(); result.addProperty("master_folio_id", folioId);
                result.addProperty("guest_folio_id", guestFolio);
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.successWithData(result));
            } catch (Exception ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }
    }

    private void routeCharge(HttpExchange exchange, long folioId) throws Exception {
        JsonObject body;
        try (InputStream input = exchange.getRequestBody()) {
            body = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        if (!body.has("charge_type") || body.get("charge_type").isJsonNull()) {
            writeError(exchange, 400, "ERR_400_BAD_REQUEST", "charge_type is required"); return;
        }
        String chargeType = body.get("charge_type").getAsString();
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int bookingId = bookingId(c, folioId);
                long routed = new FolioRoutingEngine().routeCharge(c, bookingId, chargeType);
                c.commit();
                JsonObject result = new JsonObject(); result.addProperty("booking_id", bookingId);
                result.addProperty("charge_type", chargeType); result.addProperty("folio_id", routed);
                result.addProperty("folio_type", routed == folioId ? "master" : "guest");
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(result));
            } catch (Exception ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }
    }

    private static int bookingId(Connection c, long folioId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT booking_id FROM folios WHERE id=? FOR UPDATE")) {
            q.setLong(1, folioId); try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Folio not found"); return r.getInt(1);
            }
        }
    }

    private void writeFolioList(HttpExchange exchange, int bookingId) throws SQLException, IOException {
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, folio_type, balance, status FROM folios WHERE booking_id=? ORDER BY id")) {
            statement.setInt(1, bookingId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("id", result.getLong("id"));
                    row.addProperty("folio_type", result.getString("folio_type"));
                    row.addProperty("balance", result.getBigDecimal("balance"));
                    row.addProperty("status", result.getString("status"));
                    rows.add(row);
                }
            }
        }
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(rows));
    }

    private void settle(HttpExchange exchange, long folioId) throws Exception {
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        UUID uuid;
        try {
            uuid = UUID.fromString(key == null ? "" : key);
        } catch (IllegalArgumentException ex) {
            writeError(exchange, 400, "ERR_400_BAD_REQUEST", "Idempotency-Key must be a UUIDv4");
            return;
        }
        if (uuid.version() != 4) {
            writeError(exchange, 400, "ERR_400_BAD_REQUEST", "Idempotency-Key must be a UUIDv4");
            return;
        }

        String requestBody;
        try (InputStream input = exchange.getRequestBody()) {
            requestBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        BigDecimal amount = request.has("amount") && !request.get("amount").isJsonNull()
            ? request.get("amount").getAsBigDecimal().setScale(2, RoundingMode.HALF_UP) : null;
        String paymentMethod = request.has("payment_method") ? request.get("payment_method").getAsString() : "";
        boolean checkout = request.has("checkout") && request.get("checkout").getAsBoolean();
        if (amount == null || amount.signum() < 0 || (!checkout && amount.signum() == 0)
                || paymentMethod.isBlank()) {
            writeError(exchange, 400, "ERR_400_BAD_REQUEST", "Valid amount and payment_method are required");
            return;
        }
        String requestHash = sha256("POST|" + exchange.getRequestURI().getPath() + "|" + requestBody);
        FolioDao.PaymentRequest payment = new FolioDao.PaymentRequest(folioId, key, requestHash, amount,
            paymentMethod, checkout, BUSINESS_ZONE);
        try (Connection connection = DatabaseConnection.getConnection()) {
            FolioService.PaymentResult result = new FolioService().pay(connection, payment);
            writeRaw(exchange, result.statusCode(), result.body());
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static void writeError(HttpExchange exchange, int status, String code, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error_code", code);
        error.addProperty("message", message);
        error.addProperty("timestamp", java.time.Instant.now().toString());
        writeRaw(exchange, status, JsonUtil.getGson().toJson(error));
    }

    private static void writeRaw(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
