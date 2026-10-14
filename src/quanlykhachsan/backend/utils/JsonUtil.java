package quanlykhachsan.backend.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * High-performance, memory-optimized Gson Singleton & Utility (TASK-A4.2).
 * <p>
 * Zero-allocation & stream-oriented serialization architecture:
 * <ul>
 *   <li>Pre-registered {@link com.google.gson.TypeAdapter} instances for {@link LocalDateTime}, {@link LocalDate}, and {@link BigDecimal}.</li>
 *   <li>Eliminates intermediate JsonElement tree allocations on the JVM heap.</li>
 *   <li>Guarantees exact financial precision without scientific notation.</li>
 *   <li>Maintains strict RFC-7807 and JSON Envelope consistency by serializing nulls.</li>
 * </ul>
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter())
            .disableHtmlEscaping()
            .serializeNulls() // Maintain consistent JSON Envelope structure
            .create();

    private JsonUtil() {
        // Prevent direct instantiation
    }

    /**
     * Retrieves the global singleton Gson instance configured with stream adapters.
     *
     * @return the singleton {@link Gson} instance
     */
    public static Gson getGson() {
        return GSON;
    }

    /**
     * Serializes an object to its JSON string representation.
     *
     * @param src the object to serialize
     * @return JSON string
     */
    public static String toJson(Object src) {
        return GSON.toJson(src);
    }

    /**
     * Deserializes a JSON string into an object of the specified class.
     *
     * @param json the JSON string
     * @param classOfT the target class
     * @param <T> the type of the desired object
     * @return an object of type T from the string
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return GSON.fromJson(json, classOfT);
    }

    /**
     * Deserializes a JSON string into an object of the specified generic Type.
     *
     * @param json the JSON string
     * @param typeOfT the specific generic type
     * @param <T> the type of the desired object
     * @return an object of type T from the string
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    /**
     * Deserializes JSON from a reader into an object of the specified generic Type.
     *
     * @param reader the reader supplying the JSON stream
     * @param typeOfT the specific generic type
     * @param <T> the type of the desired object
     * @return an object of type T from the reader
     */
    public static <T> T fromJson(Reader reader, Type typeOfT) {
        return GSON.fromJson(reader, typeOfT);
    }
}
