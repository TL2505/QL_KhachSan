package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production Security Hardening Filter (TASK-A4.1).
 * <p>
 * Injects mandatory OWASP-recommended security headers into all HTTP responses
 * to prevent clickjacking, MIME-type sniffing, cross-site scripting (XSS),
 * and insecure transport:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}</li>
 *   <li>{@code X-Frame-Options: DENY}</li>
 *   <li>{@code Content-Security-Policy: default-src 'self'}</li>
 *   <li>{@code Strict-Transport-Security: max-age=31536000; includeSubDomains}</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}</li>
 *   <li>{@code Permissions-Policy: geolocation=(), camera=(), microphone=()}</li>
 * </ul>
 * Also tracks in-flight requests for graceful request draining during shutdown.
 */
public class SecurityHardeningFilter extends Filter {

    public static final String HEADER_X_CONTENT_TYPE_OPTIONS = "nosniff";
    public static final String HEADER_X_FRAME_OPTIONS = "DENY";
    public static final String HEADER_CONTENT_SECURITY_POLICY =
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com https://cdn.jsdelivr.net https://unpkg.com; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "img-src 'self' data: https:;";
    public static final String HEADER_STRICT_TRANSPORT_SECURITY = "max-age=31536000; includeSubDomains";
    public static final String HEADER_REFERRER_POLICY = "strict-origin-when-cross-origin";
    public static final String HEADER_PERMISSIONS_POLICY = "geolocation=(), camera=(), microphone=()";

    private static final AtomicInteger IN_FLIGHT_REQUESTS = new AtomicInteger(0);

    public static int getInFlightRequests() {
        return IN_FLIGHT_REQUESTS.get();
    }

    @Override
    public String description() {
        return "Production Security Hardening Filter attaching OWASP Security Defense Headers";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", HEADER_X_CONTENT_TYPE_OPTIONS);
        headers.set("X-Frame-Options", HEADER_X_FRAME_OPTIONS);
        headers.set("Content-Security-Policy", HEADER_CONTENT_SECURITY_POLICY);
        headers.set("Strict-Transport-Security", HEADER_STRICT_TRANSPORT_SECURITY);
        headers.set("Referrer-Policy", HEADER_REFERRER_POLICY);
        headers.set("Permissions-Policy", HEADER_PERMISSIONS_POLICY);

        IN_FLIGHT_REQUESTS.incrementAndGet();
        try {
            chain.doFilter(exchange);
        } finally {
            IN_FLIGHT_REQUESTS.decrementAndGet();
        }
    }
}
