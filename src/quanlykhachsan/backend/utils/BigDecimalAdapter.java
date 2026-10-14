package quanlykhachsan.backend.utils;

import com.google.gson.TypeAdapter;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stream-oriented Gson TypeAdapter for {@link BigDecimal} (TASK-A4.2).
 * <p>
 * Financial precision optimization:
 * <ul>
 *   <li>Completely eliminates scientific notation (e.g. {@code 1.8E6}) in JSON outputs.</li>
 *   <li>Standardizes currency and rate amounts with a scale of 2 decimal places ({@link RoundingMode#HALF_UP}).</li>
 *   <li>Streams unquoted numeric representations directly to output without intermediate allocations.</li>
 * </ul>
 */
public class BigDecimalAdapter extends TypeAdapter<BigDecimal> {

    @Override
    public void write(JsonWriter out, BigDecimal value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            String plainNumber = value.setScale(2, RoundingMode.HALF_UP).toPlainString();
            out.value(new LazilyParsedNumber(plainNumber));
        }
    }

    @Override
    public BigDecimal read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String raw = in.nextString();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        return new BigDecimal(raw.trim());
    }
}
