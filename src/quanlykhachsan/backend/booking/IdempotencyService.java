package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.Filter.Chain;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Persistent request deduplication for booking mutations. */
public final class IdempotencyService {
    private static final int IN_PROGRESS = 0;
    private static final Duration WAIT_FOR_REQUEST = Duration.ofSeconds(30);
    private static final long POLL_INTERVAL_MILLIS = 100L;

    private IdempotencyService() { }

    public static void execute(HttpExchange exchange, Chain chain, String key) throws IOException {
        byte[] requestBody;
        try (InputStream input = exchange.getRequestBody()) {
            requestBody = input.readAllBytes();
        }

        final String requestHash;
        try {
            requestHash = sha256(exchange.getRequestMethod() + "|" + exchange.getRequestURI()
                + "|" + new String(requestBody, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            writeError(exchange, 500, "ERR_500_INTERNAL_SERVER_ERROR", "Unable to hash request");
            return;
        }

        CachedResponse cached;
        try {
            cached = claimOrRead(key, requestHash);
        } catch (SQLException ex) {
            writeError(exchange, 500, "ERR_500_INTERNAL_SERVER_ERROR", "Unable to reserve idempotency key");
            return;
        }
        if (cached != null) {
            replayOrWait(exchange, key, requestHash, cached);
            return;
        }

        BufferedExchange buffered = new BufferedExchange(exchange, requestBody);
        try {
            // The reservation is committed before business work starts, and no pool
            // connection remains checked out while the handler obtains its own connection.
            chain.doFilter(buffered);
            int status = buffered.statusCode;
            byte[] responseBody = buffered.responseBody.toByteArray();
            if (status < 100) {
                status = 500;
                responseBody = errorBody("ERR_500_INTERNAL_SERVER_ERROR", "Handler did not produce a response");
            }
            complete(key, requestHash, status, responseBody);
            writeResponse(exchange, status, responseBody);
        } catch (IOException | RuntimeException ex) {
            byte[] responseBody = errorBody("ERR_500_INTERNAL_SERVER_ERROR", "Unable to complete booking request");
            try {
                complete(key, requestHash, 500, responseBody);
                writeResponse(exchange, 500, responseBody);
            } catch (SQLException | IOException persistenceFailure) {
                ex.addSuppressed(persistenceFailure);
                writeError(exchange, 503, "ERR_503_SERVICE_UNAVAILABLE",
                    "Request outcome is being reconciled; retry with the same Idempotency-Key");
            }
        } catch (SQLException ex) {
            writeError(exchange, 503, "ERR_503_SERVICE_UNAVAILABLE",
                "Request outcome is being reconciled; retry with the same Idempotency-Key");
        }
    }

    /** Returns null to the request that successfully reserved a previously unused key. */
    private static CachedResponse claimOrRead(String key, String requestHash) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO idempotency_keys (id, request_hash, response_body, status_code) VALUES (?, ?, '', ?)")) {
                insert.setString(1, key);
                insert.setString(2, requestHash);
                insert.setInt(3, IN_PROGRESS);
                insert.executeUpdate();
                return null;
            } catch (SQLException insertFailure) {
                CachedResponse existing = find(connection, key);
                if (existing != null) return existing;
                throw insertFailure;
            }
        }
    }

    private static void replayOrWait(HttpExchange exchange, String key, String requestHash,
                                     CachedResponse cached) throws IOException {
        Instant deadline = Instant.now().plus(WAIT_FOR_REQUEST);
        while (cached.statusCode == IN_PROGRESS && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
                cached = read(key);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeError(exchange, 503, "ERR_503_SERVICE_UNAVAILABLE", "Request is still in progress");
                return;
            } catch (SQLException ex) {
                writeError(exchange, 500, "ERR_500_INTERNAL_SERVER_ERROR", "Unable to read idempotency result");
                return;
            }
            if (cached == null) {
                writeError(exchange, 503, "ERR_503_SERVICE_UNAVAILABLE", "Request reservation is unavailable");
                return;
            }
        }
        if (!requestHash.equals(cached.requestHash)) {
            writeError(exchange, 409, "ERR_409_CONFLICT", "Idempotency-Key was reused with different request data");
        } else if (cached.statusCode == IN_PROGRESS) {
            exchange.getResponseHeaders().set("Retry-After", "1");
            writeError(exchange, 503, "ERR_503_SERVICE_UNAVAILABLE", "Request is still in progress");
        } else {
            writeResponse(exchange, cached.statusCode, cached.body);
        }
    }

    private static CachedResponse read(String key) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return find(connection, key);
        }
    }

    private static CachedResponse find(Connection connection, String key) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT request_hash, response_body, status_code FROM idempotency_keys WHERE id=?")) {
            query.setString(1, key);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? new CachedResponse(rows.getString(1), rows.getString(2), rows.getInt(3)) : null;
            }
        }
    }

    private static void complete(String key, String requestHash, int status, byte[] body) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement update = connection.prepareStatement(
                 "UPDATE idempotency_keys SET response_body=?, status_code=? WHERE id=? AND request_hash=? AND status_code=?")) {
            update.setString(1, new String(body, StandardCharsets.UTF_8));
            update.setInt(2, status);
            update.setString(3, key);
            update.setString(4, requestHash);
            update.setInt(5, IN_PROGRESS);
            if (update.executeUpdate() != 1) throw new SQLException("Idempotency reservation was lost");
        }
    }

    private static void writeResponse(HttpExchange exchange, int status, byte[] body) throws IOException {
        if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        }
        if (!exchange.getResponseHeaders().containsKey("Access-Control-Allow-Origin")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        writeResponse(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeError(HttpExchange exchange, int status, String code, String message) throws IOException {
        writeResponse(exchange, status, errorBody(code, message));
    }

    private static byte[] errorBody(String code, String message) {
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        return ("{\"error_code\":\"" + code + "\",\"message\":\"" + escaped
            + "\",\"timestamp\":\"" + Instant.now() + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class CachedResponse {
        final String requestHash;
        final String body;
        final int statusCode;
        CachedResponse(String requestHash, String body, int statusCode) {
            this.requestHash = requestHash;
            this.body = body;
            this.statusCode = statusCode;
        }
    }

    private static final class BufferedExchange extends HttpExchange {
        private final HttpExchange delegate;
        private final byte[] requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int statusCode = -1;

        BufferedExchange(HttpExchange delegate, byte[] requestBody) {
            this.delegate = delegate;
            this.requestBody = requestBody;
        }
        @Override public Headers getRequestHeaders() { return delegate.getRequestHeaders(); }
        @Override public Headers getResponseHeaders() { return delegate.getResponseHeaders(); }
        @Override public URI getRequestURI() { return delegate.getRequestURI(); }
        @Override public String getRequestMethod() { return delegate.getRequestMethod(); }
        @Override public com.sun.net.httpserver.HttpContext getHttpContext() { return delegate.getHttpContext(); }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(requestBody); }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int status, long responseLength) { statusCode = status; }
        @Override public InetSocketAddress getRemoteAddress() { return delegate.getRemoteAddress(); }
        @Override public int getResponseCode() { return statusCode; }
        @Override public InetSocketAddress getLocalAddress() { return delegate.getLocalAddress(); }
        @Override public String getProtocol() { return delegate.getProtocol(); }
        @Override public Object getAttribute(String name) { return delegate.getAttribute(name); }
        @Override public void setAttribute(String name, Object value) { delegate.setAttribute(name, value); }
        @Override public void setStreams(InputStream input, OutputStream output) { delegate.setStreams(input, output); }
        @Override public HttpPrincipal getPrincipal() { return delegate.getPrincipal(); }
    }
}
