package quanlykhachsan.backend.utils;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Stream-oriented Gson TypeAdapter for {@link LocalDate} (TASK-A4.2).
 * <p>
 * Standardizes date formatting to ISO-8601 {@code "yyyy-MM-dd"}.
 */
public class LocalDateAdapter extends TypeAdapter<LocalDate> {

    @Override
    public void write(JsonWriter out, LocalDate value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }

    @Override
    public LocalDate read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String raw = in.nextString();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
