package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-Performance HTTP Exchange Helper (TASK-A3.3).
 * <p>
 * Provides zero-redundancy, memory-safe data extraction utilities for HttpServer requests:
 * <ul>
 *   <li>Fast query parameter parsing avoiding superfluous array/object allocations.</li>
 *   <li>Case-insensitive header retrieval.</li>
 *   <li>Buffered request body streaming with pre-allocated 4096-byte chunking.</li>
 * </ul>
 */
public class HttpExchangeHelper {

    private static final int BUFFER_SIZE = 4096;

    private HttpExchangeHelper() {
        // Utility class
    }

    /**
     * Parses URI query parameters safely into an immutable Map.
     * Returns {@link Collections#emptyMap()} immediately if no query string is present.
     */
    public static Map<String, String> parseQueryParams(HttpExchange exchange) {
        if (exchange == null) {
            return Collections.emptyMap();
        }
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> params = new LinkedHashMap<>();
        int len = rawQuery.length();
        int start = 0;

        while (start < len) {
            int amp = rawQuery.indexOf('&', start);
            int end = (amp == -1) ? len : amp;
            int eq = rawQuery.indexOf('=', start);

            if (eq != -1 && eq < end) {
                String rawKey = rawQuery.substring(start, eq);
                String rawVal = rawQuery.substring(eq + 1, end);
                try {
                    String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(rawVal, StandardCharsets.UTF_8);
                    params.put(key, val);
                } catch (Exception ignored) {
                    params.put(rawKey, rawVal);
                }
            } else if (start < end) {
                String rawKey = rawQuery.substring(start, end);
                try {
                    String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                    params.put(key, "");
                } catch (Exception ignored) {
                    params.put(rawKey, "");
                }
            }
            start = end + 1;
        }

        return Collections.unmodifiableMap(params);
    }

    /**
     * Retrieves an HTTP header value using case-insensitive key lookup.
     */
    public static String getHeader(HttpExchange exchange, String headerName) {
        if (exchange == null || headerName == null || headerName.isEmpty()) {
            return null;
        }
        Headers headers = exchange.getRequestHeaders();
        if (headers == null) {
            return null;
        }

        // Direct lookup first (Headers is usually case-insensitive in com.sun.net.httpserver)
        String direct = headers.getFirst(headerName);
        if (direct != null) {
            return direct.trim();
        }

        // Case-insensitive fallback iteration
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (headerName.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    return values.get(0).trim();
                }
            }
        }
        return null;
    }

    /**
     * Reads the entire HTTP request body using an optimal 4096-byte buffer and UTF-8 charset.
     */
    public static String readRequestBody(HttpExchange exchange) throws IOException {
        if (exchange == null) {
            return "";
        }
        try (InputStream is = exchange.getRequestBody()) {
            if (is == null) {
                return "";
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}
