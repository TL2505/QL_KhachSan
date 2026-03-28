package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class DashboardAPI {

    public static JsonObject getDashboardStats() {
        try {
            String jsonResponse = HttpUtil.sendGet("/dashboard");
            return new Gson().fromJson(jsonResponse, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", e.getMessage());
            return err;
        }
    }
}
