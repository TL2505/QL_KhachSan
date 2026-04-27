package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.JsonUtil;
import quanlykhachsan.backend.model.DailyStats;
import quanlykhachsan.backend.model.MonthlyRevenue;
import quanlykhachsan.backend.model.DashboardData;
import quanlykhachsan.backend.model.DashboardFilter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ReportAPI {

    public static List<MonthlyRevenue> getMonthlyRevenue() throws Exception {
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendGet("/reports/monthly-revenue");
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            Type listType = new TypeToken<ArrayList<MonthlyRevenue>>(){}.getType();
            return JsonUtil.getGson().fromJson(resObj.get("data"), listType);
        }
        throw new Exception("Lỗi khi tải dữ liệu báo cáo!");
    }

    public static DailyStats getTodayStats() throws Exception {
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendGet("/reports/today-stats");
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server!");
        }
        
        JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            return JsonUtil.getGson().fromJson(resObj.get("data"), DailyStats.class);
        }
        throw new Exception("Lỗi khi tải dữ liệu thống kê ngày!");
    }

    public static int getActiveAccountCount() throws Exception {
        String jsonResponse;
        try {
            jsonResponse = HttpUtil.sendGet("/reports/active-accounts");
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            return resObj.get("data").getAsInt();
        }
        throw new Exception("Lỗi khi tải số liệu tài khoản kích hoạt!");
    }

    public static DashboardData getDashboardData(DashboardFilter filter) throws Exception {
        String jsonResponse;
        try {
            String requestBody = JsonUtil.getGson().toJson(filter);
            jsonResponse = HttpUtil.sendPost("/reports/dashboard", requestBody);
        } catch (java.net.ConnectException ex) {
            throw new Exception("Không thể kết nối Backend Server! (Connection Refused)");
        }
        
        JsonObject resObj = JsonUtil.getGson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            return JsonUtil.getGson().fromJson(resObj.get("data"), DashboardData.class);
        }
        throw new Exception("Lỗi khi tải dữ liệu phân tích Dashboard!");
    }
}
