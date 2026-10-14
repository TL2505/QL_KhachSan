package quanlykhachsan.backend.utils;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Stream-oriented Gson TypeAdapter for {@link LocalDateTime} (TASK-A4.2).
 * <p>
 * Zero-allocation streaming optimization:
 * <ul>
 *   <li>Inherits directly from {@link TypeAdapter} instead of JsonSerializer/JsonDeserializer
 *       to prevent intermediate JsonElement tree allocations on the JVM heap.</li>
 *   <li>Formats output using standard {@code "yyyy-MM-dd HH:mm:ss"}.</li>
 *   <li>Supports flexible decoding for both ISO-8601 ({@code "yyyy-MM-dd'T'HH:mm:ss"}) and standard formats.</li>
 * </ul>
 */
public class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {

    public static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void write(JsonWriter out, LocalDateTime value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.format(STANDARD_FORMATTER));
        }
    }

    @Override
    public LocalDateTime read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String raw = in.nextString();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String str = raw.trim();
        if (str.contains("T")) {
            // ISO-8601 format (e.g. 2026-10-14T00:02:15 or 2026-10-14T00:02:15Z)
            if (str.endsWith("Z")) {
                str = str.substring(0, str.length() - 1);
            }
            return LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else if (str.contains(" ")) {
            return LocalDateTime.parse(str, STANDARD_FORMATTER);
        } else if (str.length() == 10) {
            // Date-only fallback: yyyy-MM-dd -> start of day
            return LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } else {
            return LocalDateTime.parse(str);
        }
    }
}
