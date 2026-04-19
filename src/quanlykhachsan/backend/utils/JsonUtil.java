package quanlykhachsan.backend.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Utility class to provide a consistent Gson instance across the backend.
 * Standardizes date formatting to prevent locale-specific parsing errors.
 */
public class JsonUtil {
    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    public static Gson getGson() {
        return gson;
    }
}
