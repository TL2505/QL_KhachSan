package quanlykhachsan.backend.customer;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.regex.Pattern;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

/** Staff-only read routes; the existing earn/redeem/tier workflows are untouched. */
public final class LoyaltyDirectoryController implements HttpHandler {
    private static final Pattern HISTORY = Pattern.compile("/api/loyalty/members/([1-9][0-9]*)/history");
    private final LoyaltyDirectoryService service;
    public LoyaltyDirectoryController() { this(new LoyaltyDirectoryService()); }
    public LoyaltyDirectoryController(LoyaltyDirectoryService service) { this.service = service; }

    @Override public void handle(HttpExchange exchange) throws IOException {
        // Use verified JWT attributes, never the caller-controlled X-User-Role header.
        if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "This directory is read-only"); return;
        }
        String path = exchange.getRequestURI().getPath().replaceFirst("^/api/v1/loyalty", "/api/loyalty");
        try {
            if (path.equals("/api/loyalty/members")) {
                ApiResponseUtil.sendSuccess(exchange, 200, service.directory()); return;
            }
            var matcher = HISTORY.matcher(path);
            if (!matcher.matches()) {
                ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Loyalty directory route not found"); return;
            }
            var result = service.history(Integer.parseInt(matcher.group(1)));
            if (result == null) {
                ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Customer not found"); return;
            }
            ApiResponseUtil.sendSuccess(exchange, 200, result);
        } catch (IllegalArgumentException error) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid customer ID");
        } catch (SQLException error) {
            ApiResponseUtil.sendError(exchange, 503, "ERR_503_UNAVAILABLE", "Unable to load loyalty data; please retry");
        }
    }
}
