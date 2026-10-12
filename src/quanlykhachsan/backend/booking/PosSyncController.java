package quanlykhachsan.backend.booking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

/** Atomically posts a batch of offline POS debits using one idempotency key per charge. */
public final class PosSyncController implements HttpHandler {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        try {
            if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
            String requestBody;
            try (InputStream input = exchange.getRequestBody()) {
                requestBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
            JsonArray charges = request.getAsJsonArray("charges");
            if (charges == null || charges.size() == 0) {
                writeError(exchange, 400, "ERR_400_BAD_REQUEST", "At least one charge is required");
                return;
            }
            LocalDate businessDate = LocalDate.now(BUSINESS_ZONE);
            JsonArray posted = new JsonArray();
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    for (JsonElement element : charges) {
                        JsonObject charge = element.getAsJsonObject();
                        String key = charge.has("idempotency_key") ? charge.get("idempotency_key").getAsString() : "";
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(key);
                        } catch (IllegalArgumentException ex) {
                            throw new ClientInputException(400, "ERR_400_BAD_REQUEST", "Each charge needs a UUIDv4 idempotency_key");
                        }
                        if (uuid.version() != 4) {
                            throw new ClientInputException(400, "ERR_400_BAD_REQUEST", "Each charge needs a UUIDv4 idempotency_key");
                        }
                        int folioId = charge.get("folio_id").getAsInt();
                        BigDecimal amount = charge.get("amount").getAsBigDecimal();
                        String chargeType = charge.get("charge_type").getAsString();
                        String description = charge.get("description").getAsString();
                        if (folioId < 1 || amount.signum() <= 0 || chargeType.isBlank() || description.isBlank()) {
                            throw new ClientInputException(400, "ERR_400_BAD_REQUEST", "Charge fields are invalid");
                        }
                        String hash = sha256("POS|" + folioId + "|" + chargeType + "|" + description + "|" + amount.toPlainString());
                        String previousHash = null;
                        try (PreparedStatement existing = connection.prepareStatement(
                                "SELECT request_hash FROM idempotency_keys WHERE id=? FOR UPDATE")) {
                            existing.setString(1, key);
                            try (ResultSet rows = existing.executeQuery()) {
                                if (rows.next()) previousHash = rows.getString(1);
                            }
                        }
                        if (previousHash != null) {
                            if (!previousHash.equals(hash)) {
                                throw new ClientInputException(409, "ERR_409_CONFLICT", "Idempotency key was reused with different charge data");
                            }
                            JsonObject duplicate = new JsonObject();
                            duplicate.addProperty("idempotency_key", key);
                            duplicate.addProperty("duplicate", true);
                            posted.add(duplicate);
                            continue;
                        }
                        try (PreparedStatement reserve = connection.prepareStatement(
                                "INSERT INTO idempotency_keys (id, request_hash, response_body, status_code) VALUES (?, ?, '{}', 201)")) {
                            reserve.setString(1, key);
                            reserve.setString(2, hash);
                            reserve.executeUpdate();
                        }
                        String status;
                        try (PreparedStatement folio = connection.prepareStatement(
                                "SELECT status FROM folios WHERE id=? FOR UPDATE")) {
                            folio.setInt(1, folioId);
                            try (ResultSet rows = folio.executeQuery()) {
                                if (!rows.next()) throw new ClientInputException(404, "ERR_404_NOT_FOUND", "Folio not found");
                                status = rows.getString(1);
                            }
                        }
                        if (!"open".equals(status)) {
                            throw new ClientInputException(409, "ERR_409_CONFLICT", "Folio is not open");
                        }
                        try (PreparedStatement transaction = connection.prepareStatement(
                                "INSERT INTO folio_transactions (folio_id, transaction_type, charge_type, description, amount, business_date, reference_id)"
                              + " VALUES (?, 'debit', ?, ?, ?, ?, ?)")) {
                            transaction.setInt(1, folioId);
                            transaction.setString(2, chargeType);
                            transaction.setString(3, description);
                            transaction.setBigDecimal(4, amount);
                            transaction.setDate(5, java.sql.Date.valueOf(businessDate));
                            transaction.setString(6, key);
                            transaction.executeUpdate();
                        }
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE folios SET balance=balance+? WHERE id=? AND status='open'")) {
                            update.setBigDecimal(1, amount);
                            update.setInt(2, folioId);
                            if (update.executeUpdate() != 1) throw new ClientInputException(409, "ERR_409_CONFLICT", "Folio changed while syncing");
                        }
                        JsonObject result = new JsonObject();
                        result.addProperty("idempotency_key", key);
                        result.addProperty("folio_id", folioId);
                        result.addProperty("business_date", businessDate.toString());
                        result.addProperty("duplicate", false);
                        JsonObject saved = new JsonObject();
                        saved.addProperty("status", "success");
                        saved.add("data", result.deepCopy());
                        try (PreparedStatement save = connection.prepareStatement(
                                "UPDATE idempotency_keys SET response_body=? WHERE id=?")) {
                            save.setString(1, JsonUtil.getGson().toJson(saved));
                            save.setString(2, key);
                            save.executeUpdate();
                        }
                        posted.add(result);
                    }
                    JsonObject data = new JsonObject();
                    data.add("charges", posted);
                    data.addProperty("business_date", businessDate.toString());
                    connection.commit();
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
                } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (ClientInputException ex) {
            writeError(exchange, ex.status, ex.code, ex.getMessage());
        } catch (java.sql.SQLException ex) {
            int code = ex.getErrorCode() == 1062 ? 409 : 500;
            writeError(exchange, code, code == 409 ? "ERR_409_CONFLICT" : "ERR_500_INTERNAL_SERVER_ERROR",
                code == 409 ? "Duplicate sync request; retry with the same idempotency keys" : "Unable to sync POS charges");
        } catch (Exception ex) {
            writeError(exchange, 500, "ERR_500_INTERNAL_SERVER_ERROR", "Unable to sync POS charges");
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
        ApiResponseUtil.write(exchange, status, JsonUtil.getGson().toJson(error));
    }

    private static final class ClientInputException extends Exception {
        final int status;
        final String code;
        ClientInputException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
