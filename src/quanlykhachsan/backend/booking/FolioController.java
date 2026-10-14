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
    private static final Pattern BOOKING_FOLIOS_PATH = Pattern.compile("/api/v1/folios/booking/(\\d+)");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
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
                writeError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
                return;
            }
            if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
            Matcher matcher = PAYMENT_PATH.matcher(path);
            if (!matcher.matches()) {
                writeError(exchange, 404, "ERR_404_NOT_FOUND", "Folio route not found");
                return;
            }
            settle(exchange, Integer.parseInt(matcher.group(1)));
        } catch (IllegalArgumentException ex) {
            writeError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid folio request");
        } catch (Exception ex) {
            System.err.println("[FolioController] Request failed: " + ex.getClass().getSimpleName());
            if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                writeError(exchange, 500, "ERR_500_INTERNAL_SERVER_ERROR", "Unable to process folio request");
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
                    row.addProperty("id", result.getInt("id"));
                    row.addProperty("folio_type", result.getString("folio_type"));
                    row.addProperty("balance", result.getBigDecimal("balance"));
                    row.addProperty("status", result.getString("status"));
                    rows.add(row);
                }
            }
        }
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(rows));
    }

    private void settle(HttpExchange exchange, int folioId) throws Exception {
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

        try (Connection connection = DatabaseConnection.getConnection()) {
            acquireIdempotencyLock(connection, key);
            connection.setAutoCommit(false);
            try {
                String oldHash = null;
                String oldBody = null;
                int oldCode = 200;
                try (PreparedStatement existing = connection.prepareStatement(
                        "SELECT request_hash, response_body, status_code FROM idempotency_keys WHERE id=? FOR UPDATE")) {
                    existing.setString(1, key);
                    try (ResultSet result = existing.executeQuery()) {
                        if (result.next()) {
                            oldHash = result.getString("request_hash");
                            oldBody = result.getString("response_body");
                            oldCode = result.getInt("status_code");
                        }
                    }
                }
                if (oldHash != null) {
                    connection.rollback();
                    if (!oldHash.equals(requestHash)) {
                        writeError(exchange, 409, "ERR_409_CONFLICT", "Idempotency-Key was used with a different request");
                    } else {
                        writeRaw(exchange, oldCode, oldBody);
                    }
                    return;
                }
                try (PreparedStatement reserve = connection.prepareStatement(
                        "INSERT INTO idempotency_keys (id, request_hash, response_body, status_code) VALUES (?, ?, '{}', 200)")) {
                    reserve.setString(1, key);
                    reserve.setString(2, requestHash);
                    reserve.executeUpdate();
                }

                int bookingId;
                int roomId;
                BigDecimal currentBalance;
                try (PreparedStatement folio = connection.prepareStatement(
                        "SELECT f.booking_id, f.balance, b.room_id FROM folios f JOIN bookings b ON b.id=f.booking_id WHERE f.id=? FOR UPDATE")) {
                    folio.setInt(1, folioId);
                    try (ResultSet result = folio.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            writeError(exchange, 404, "ERR_404_NOT_FOUND", "Folio not found");
                            return;
                        }
                        bookingId = result.getInt("booking_id");
                        roomId = result.getInt("room_id");
                        currentBalance = result.getBigDecimal("balance");
                    }
                }
                if (amount.compareTo(currentBalance) > 0) {
                    connection.rollback();
                    writeError(exchange, 409, "ERR_409_CONFLICT", "Payment exceeds the folio balance");
                    return;
                }
                BigDecimal newBalance = currentBalance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
                if (amount.signum() > 0) {
                    try (PreparedStatement transaction = connection.prepareStatement(
                            "INSERT INTO folio_transactions (folio_id, transaction_type, charge_type, description, amount, business_date, reference_id)"
                          + " VALUES (?, 'credit', 'payment', ?, ?, ?, ?)")) {
                        transaction.setInt(1, folioId);
                        transaction.setString(2, "Checkout payment (" + paymentMethod + ")");
                        transaction.setBigDecimal(3, amount);
                        transaction.setDate(4, java.sql.Date.valueOf(LocalDate.now(BUSINESS_ZONE)));
                        transaction.setString(5, key);
                        transaction.executeUpdate();
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE folios SET balance=?, status=? WHERE id=?")) {
                    update.setBigDecimal(1, newBalance);
                    update.setString(2, newBalance.signum() == 0 ? "settled" : "open");
                    update.setInt(3, folioId);
                    if (update.executeUpdate() != 1) throw new SQLException("Folio state changed before payment was applied");
                }

                boolean checkedOut = false;
                if (checkout) {
                    BigDecimal totalBalance;
                    try (PreparedStatement total = connection.prepareStatement(
                            "SELECT COALESCE(SUM(balance), 0.00) FROM folios WHERE booking_id=? FOR UPDATE")) {
                        total.setInt(1, bookingId);
                        try (ResultSet result = total.executeQuery()) {
                            result.next();
                            totalBalance = result.getBigDecimal(1);
                        }
                    }
                    if (totalBalance.signum() == 0) {
                        try (PreparedStatement booking = connection.prepareStatement(
                                "UPDATE bookings SET status='checked_out' WHERE id=? AND status='checked_in'")) {
                            booking.setInt(1, bookingId);
                            booking.executeUpdate();
                        }
                        try (PreparedStatement room = connection.prepareStatement(
                                "UPDATE rooms SET status='vacant_dirty', version=version+1 WHERE id=? AND status IN ('occupied_clean','occupied_dirty')")) {
                            room.setInt(1, roomId);
                            int changed = room.executeUpdate();
                            if (changed != 1) throw new SQLException("Room state changed before checkout");
                        }
                        checkedOut = true;
                    }
                }
                JsonObject data = new JsonObject();
                data.addProperty("folio_id", folioId);
                data.addProperty("booking_id", bookingId);
                data.addProperty("balance", newBalance);
                data.addProperty("checked_out", checkedOut);
                JsonObject envelope = new JsonObject();
                envelope.addProperty("status", "success");
                envelope.addProperty("message", "Payment recorded");
                envelope.add("data", data);
                String responseBody = JsonUtil.getGson().toJson(envelope);
                try (PreparedStatement save = connection.prepareStatement(
                        "UPDATE idempotency_keys SET response_body=?, status_code=200 WHERE id=?")) {
                    save.setString(1, responseBody);
                    save.setString(2, key);
                    save.executeUpdate();
                }
                connection.commit();
                writeRaw(exchange, 200, responseBody);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } finally {
                    releaseIdempotencyLock(connection, key);
                }
            }
        }
    }

    private static void acquireIdempotencyLock(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(SHA2(?, 256), 30)")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("Timed out acquiring payment idempotency lock");
                }
            }
        }
    }

    private static void releaseIdempotencyLock(Connection connection, String key) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(SHA2(?, 256))")) {
            statement.setString(1, key);
            try (ResultSet ignored = statement.executeQuery()) {
                // Consume and close the result so the pooled connection is returned cleanly.
            }
        } catch (SQLException ex) {
            System.err.println("[FolioController] Unable to release payment idempotency lock: "
                + ex.getMessage());
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
