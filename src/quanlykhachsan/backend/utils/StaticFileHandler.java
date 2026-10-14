package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Serves only files contained in the public asset directory. */
public final class StaticFileHandler implements HttpHandler {
    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                respond(exchange, 405, "Method not allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            // Reject traversal before normalization, including percent-decoded segments.
            if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0
                    || java.util.Arrays.asList(path.split("/", -1)).contains("..")) {
                respond(exchange, 403, "Forbidden");
                return;
            }
            String relative;
            if (path.equals("/") || path.equals("/booking") || path.equals("/booking/")) {
                relative = "index.html";
            } else if (path.startsWith("/booking/")) {
                relative = path.substring("/booking/".length());
            } else if (path.startsWith("/api/")) {
                respond(exchange, 404, "Not found");
                return;
            } else {
                relative = path.substring(1);
            }
            // Colons can address Windows drive paths or alternate data streams.
            if (relative.indexOf(':') >= 0) {
                respond(exchange, 403, "Forbidden");
                return;
            }
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root)) {
                respond(exchange, 403, "Forbidden");
                return;
            }
            if (!Files.exists(file) || !Files.exists(root)) {
                respond(exchange, 404, "Not found");
                return;
            }
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                respond(exchange, 403, "Forbidden");
                return;
            }
            if (!Files.isRegularFile(file)) {
                respond(exchange, 404, "Not found");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", mimeType(file));
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            long size = Files.size(file);
            exchange.getResponseHeaders().set("Content-Length", Long.toString(size));
            exchange.sendResponseHeaders(200, method.equals("HEAD") || size == 0 ? -1 : size);
            if (method.equals("GET") && size > 0) {
                Files.copy(file, exchange.getResponseBody());
            }
        }
    }

    private static String mimeType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, exchange.getRequestMethod().equals("HEAD") ? -1 : body.length);
        if (!exchange.getRequestMethod().equals("HEAD")) exchange.getResponseBody().write(body);
    }
}
