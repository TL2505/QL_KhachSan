package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

/** Mounted behind JwtFilter; success means server-side revocation has committed. */
public final class LogoutController implements HttpHandler {
    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"/api/v1/auth/logout".equals(exchange.getRequestURI().getPath())) {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Route not found"); return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed"); return;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Bearer token required"); return;
        }
        String token = authorization.substring(7).trim();
        var claims = SecurityUtil.validateAndExtractClaims(token);
        if (claims.getExpiration() == null || !TokenBlacklistService.shared().revoke(token, claims.getExpiration().toInstant())) {
            ApiResponseUtil.sendError(exchange, 503, "ERR_503_REVOCATION_UNAVAILABLE", "Logout could not be completed; retry"); return;
        }
        ApiResponseUtil.sendSuccess(exchange, 200, java.util.Map.of("logged_out", true));
    }
}
