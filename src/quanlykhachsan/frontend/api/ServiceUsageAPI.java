package quanlykhachsan.frontend.api;

import com.google.gson.reflect.TypeToken;
import quanlykhachsan.backend.model.ServiceUsage;
import quanlykhachsan.frontend.utils.HttpUtil;

import java.util.ArrayList;
import java.util.List;

public class ServiceUsageAPI {
    private static final String API_URL = "/service-usage";

    private static final com.google.gson.Gson gson = new com.google.gson.Gson();

    public static List<ServiceUsage> getUsageByBooking(int bookingId) {
        try {
            String json = HttpUtil.sendGet(API_URL + "?bookingId=" + bookingId);
            if (json != null && !json.isEmpty() && !json.contains("error")) {
                return gson.fromJson(json, new TypeToken<ArrayList<ServiceUsage>>() {
                }.getType());
            }
        } catch (Exception e) {
        }
        return new ArrayList<>();
    }

    public static String addUsage(ServiceUsage u) {
        try {
            return HttpUtil.sendPost(API_URL, gson.toJson(u));
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    public static String deleteUsage(int usageId) {
        try {
            return HttpUtil.sendDelete(API_URL + "/" + usageId);
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }
}
