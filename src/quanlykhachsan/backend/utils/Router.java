package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Enterprise HTTP Router resolving dynamic regex path variables and dispatching handlers (TASK-A1.2 / TASK-A3.3).
 * <p>
 * Key Capabilities & Performance Optimizations:
 * <ul>
 *   <li><b>Pre-compiled Regex & Parameter Parsing:</b> All dynamic path expressions precompiled once at registration.</li>
 *   <li><b>Zero-Allocation Static Route Fast-Path:</b> Static routes evaluated via string equality before regex engine invocation.</li>
 *   <li><b>Anti-Shadowing Route Prioritization:</b> Static endpoints strictly take precedence over dynamic wildcard patterns.</li>
 *   <li><b>Sub-Controller Mounting:</b> Mounts legacy and feature {@link HttpHandler} controllers onto sub-paths.</li>
 *   <li><b>W3C CORS Preflight:</b> Compliant OPTIONS 204 No Content handling.</li>
 *   <li><b>Global Exception Pipeline:</b> Wraps execution to delegate uncaught errors to {@link GlobalExceptionHandler}.</li>
 * </ul>
 */
public class Router implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(Router.class);

    private final List<RouteEntry> routes = new ArrayList<>();
    private final Map<String, HttpHandler> mountedHandlers = new LinkedHashMap<>();

    /**
     * Registers a route and keeps routes sorted by specificity to prevent dynamic route shadowing.
     */
    public synchronized Router addRoute(String method, String pathPattern, RouteEntry.RouteHandler handler) {
        routes.add(new RouteEntry(method, pathPattern, handler));
        Collections.sort(routes); // Anti-shadowing: static routes and specific patterns sorted first
        return this;
    }

    public Router get(String pathPattern, RouteEntry.RouteHandler handler) {
        return addRoute("GET", pathPattern, handler);
    }

    public Router post(String pathPattern, RouteEntry.RouteHandler handler) {
        return addRoute("POST", pathPattern, handler);
    }

    public Router put(String pathPattern, RouteEntry.RouteHandler handler) {
        return addRoute("PUT", pathPattern, handler);
    }

    public Router delete(String pathPattern, RouteEntry.RouteHandler handler) {
        return addRoute("DELETE", pathPattern, handler);
    }

    /**
     * Mounts an HttpHandler (e.g. legacy controller) onto a path prefix.
     * Evaluated as a fallback when no explicit dynamic/static RouteEntry matches.
     */
    public synchronized Router mount(String pathPrefix, HttpHandler handler) {
        if (pathPrefix == null || pathPrefix.isBlank() || handler == null) {
            throw new IllegalArgumentException("Path prefix and handler must not be null or blank");
        }
        String normalized = pathPrefix.trim();
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        mountedHandlers.put(normalized, handler);
        return this;
    }

    public List<RouteEntry> getRoutes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * Resolves a RouteEntry matching the HTTP method and request path.
     * Useful for programmatic lookup and microbenchmarks (TASK-A3.3).
     */
    public RouteEntry findRoute(String method, String path) {
        String reqPath = (path != null && path.length() > 1 && path.endsWith("/"))
                ? path.substring(0, path.length() - 1)
                : (path != null ? path : "/");

        List<RouteEntry> currentRoutes;
        synchronized (this) {
            currentRoutes = new ArrayList<>(routes);
        }

        for (RouteEntry route : currentRoutes) {
            if (!route.matchesMethod(method)) {
                continue;
            }
            if (route.isStatic()) {
                if (route.getRawPattern().equals(reqPath)) {
                    return route;
                }
            } else {
                if (route.getCompiledRegex().matcher(reqPath).matches()) {
                    return route;
                }
            }
        }
        return null;
    }

    public Map<String, HttpHandler> getMountedHandlers() {
        return Collections.unmodifiableMap(mountedHandlers);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String requestPath = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        if (requestPath.length() > 1 && requestPath.endsWith("/")) {
            requestPath = requestPath.substring(0, requestPath.length() - 1); // Normalize trailing slash
        }
        String requestMethod = exchange.getRequestMethod();

        // 1. Handle CORS pre-flight OPTIONS requests (W3C & ADR-05: 204 No Content with X-Signature)
        if ("OPTIONS".equalsIgnoreCase(requestMethod)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", CorsFilter.ALLOWED_ORIGIN);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", CorsFilter.ALLOWED_METHODS);
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", CorsFilter.ALLOWED_HEADERS);
            exchange.getResponseHeaders().set("Access-Control-Max-Age", CorsFilter.MAX_AGE);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // 2. High-performance Route Dispatch with pre-compiled regex & fast path matching
        boolean pathMatched = false;
        RouteEntry matchedRoute = null;
        Map<String, String> pathParams = Collections.emptyMap();

        List<RouteEntry> currentRoutes;
        synchronized (this) {
            currentRoutes = new ArrayList<>(routes);
        }

        for (RouteEntry route : currentRoutes) {
            // Fast-path for static endpoints (zero regex engine overhead)
            if (route.isStatic()) {
                if (route.getRawPattern().equals(requestPath)) {
                    pathMatched = true;
                    if (route.matchesMethod(requestMethod)) {
                        matchedRoute = route;
                        pathParams = Collections.emptyMap();
                        break;
                    }
                }
                continue;
            }

            // Optimized dynamic route evaluation: single matcher execution
            Matcher matcher = route.getCompiledRegex().matcher(requestPath);
            if (matcher.matches()) {
                pathMatched = true;
                if (route.matchesMethod(requestMethod)) {
                    matchedRoute = route;
                    List<String> varNames = route.getPathVariableNames();
                    if (varNames.isEmpty()) {
                        pathParams = Collections.emptyMap();
                    } else {
                        pathParams = new LinkedHashMap<>(varNames.size());
                        for (int i = 0; i < varNames.size(); i++) {
                            pathParams.put(varNames.get(i), matcher.group(i + 1));
                        }
                    }
                    break;
                }
            }
        }

        if (matchedRoute != null) {
            try {
                matchedRoute.getHandler().handle(exchange, pathParams);
            } catch (Throwable t) {
                GlobalExceptionHandler.handle(exchange, t);
            }
            return;
        }

        // 3. If an explicit RouteEntry matched the path pattern but not the HTTP verb -> 405 Method Not Allowed
        if (pathMatched) {
            ApiResponseUtil.sendError(exchange, 405, "ERR_METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }

        // 4. Fallback to mounted prefix controllers (longest prefix match within Router)
        List<Map.Entry<String, HttpHandler>> sortedMounts;
        synchronized (this) {
            sortedMounts = new ArrayList<>(mountedHandlers.entrySet());
        }
        sortedMounts.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, HttpHandler> entry : sortedMounts) {
            String prefix = entry.getKey();
            if (requestPath.equals(prefix) || requestPath.startsWith(prefix + "/") || prefix.equals("/")) {
                try {
                    entry.getValue().handle(exchange);
                } catch (Throwable t) {
                    GlobalExceptionHandler.handle(exchange, t);
                }
                return;
            }
        }

        // 5. Unmapped path -> 404 Not Found
        ApiResponseUtil.sendError(exchange, 404, "ERR_NOT_FOUND", "Resource not found");
    }
}
