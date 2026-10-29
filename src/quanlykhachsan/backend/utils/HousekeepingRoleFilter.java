package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Limits a housekeeping login to its work queue and its own account settings. */
public final class HousekeepingRoleFilter extends Filter {
    private static final Set<String> ACCOUNT_METHODS = Set.of("GET", "POST", "PUT", "DELETE");

    @Override
    public String description() {
        return "Housekeeping role route allowlist";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String role = String.valueOf(exchange.getAttribute("role"));
        if (!"housekeeping".equals(role.toLowerCase(Locale.ROOT)) || allowed(exchange)) {
            chain.doFilter(exchange);
            return;
        }
        ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Housekeeping account access is restricted to housekeeping tasks and personal account settings");
    }

    private static boolean allowed(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(method) && "/api/v1/housekeeping/tasks".equals(path)) return true;
        if ("POST".equals(method) && path.matches("/api/v1/housekeeping/tasks/[0-9]+/(start|complete)")) return true;
        if (path.matches("/api/v1/account(?:/.*)?") && ACCOUNT_METHODS.contains(method)) return true;
        return ("PUT".equals(method) || "POST".equals(method))
                && Set.of("/api/v1/users/update-profile", "/api/v1/users/change-password",
                        "/api/v1/users/update-theme", "/api/v1/auth/change-password").contains(path);
    }
}
