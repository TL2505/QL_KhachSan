package quanlykhachsan.frontend.api;

import com.google.gson.reflect.TypeToken;
import quanlykhachsan.backend.model.Service;
import quanlykhachsan.frontend.utils.HttpUtil;

import java.util.ArrayList;
import java.util.List;

public class ServiceAPI {
    private static final String API_URL = "/services";

    private static final com.google.gson.Gson gson = new com.google.gson.Gson();

    public static List<Service> getAllServices() {
        try {
            String json = HttpUtil.sendGet(API_URL);
            if (json != null && !json.isEmpty() && !json.contains("error")) {
                return gson.fromJson(json, new TypeToken<ArrayList<Service>>() {
                }.getType());
            }
        } catch (Exception e) {
        }
        return new ArrayList<>();
    }

    public static String addService(Service s) {
        try {
            return HttpUtil.sendPost(API_URL, gson.toJson(s));
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    public static String updateService(Service s) {
        try {
            return HttpUtil.sendPut(API_URL, gson.toJson(s));
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    public static String deleteService(int id) {
        try {
            return HttpUtil.sendDelete(API_URL + "/" + id);
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }
}
