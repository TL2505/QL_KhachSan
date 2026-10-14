package quanlykhachsan.backend.utils;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Bounded, strict parsing shared by the public guest APIs. */
public final class PortalValidation {
    private PortalValidation() { }
    public static JsonObject body(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(16385);
        if (bytes.length > 16384) throw new IllegalArgumentException("Request body is too large");
        try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid JSON body"); }
    }
    public static String text(JsonObject body, String name, int max, boolean required) {
        JsonElement value = body.get(name);
        if (value == null || value.isJsonNull()) {
            if (required) throw new IllegalArgumentException("Missing " + name);
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid " + name);
        String text = value.getAsString().trim();
        if ((required && text.isEmpty()) || text.length() > max || text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid " + name);
        return text.isEmpty() && !required ? null : text;
    }
    public static String phone(String value) {
        if (value == null || !value.matches("(?:0|\\+84)[35789][0-9]{8}")) throw new IllegalArgumentException("Invalid phone");
        return value.startsWith("+84") ? "0" + value.substring(3) : value;
    }
    public static String email(String value) {
        if (value != null && (value.length() > 100 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) throw new IllegalArgumentException("Invalid email");
        return value;
    }
    public static int integer(JsonObject body, String name, int min) {
        try {
            JsonElement value = body.get(name);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new NumberFormatException();
            int number = value.getAsBigDecimal().intValueExact();
            if (number < min) throw new NumberFormatException();
            return number;
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid " + name); }
    }
    public static Map<String,String> query(HttpExchange e) {
        Map<String,String> result = new HashMap<>();
        String raw = e.getRequestURI().getRawQuery();
        if (raw == null) return result;
        for (String part : raw.split("&")) {
            String[] pair = part.split("=",2);
            if (pair.length != 2) throw new IllegalArgumentException("Invalid query");
            String name = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (result.put(name, java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8)) != null)
                throw new IllegalArgumentException("Duplicate query parameter");
        }
        return result;
    }
    public static DateRange range(String in, String out) {
        try {
            LocalDate start = LocalDate.parse(in), end = LocalDate.parse(out);
            long nights = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            if (start.isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))) || nights < 1 || nights > 365) throw new IllegalArgumentException();
            return new DateRange(start,end);
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid stay dates"); }
    }
    public record DateRange(LocalDate checkIn, LocalDate checkOut) { }
}
