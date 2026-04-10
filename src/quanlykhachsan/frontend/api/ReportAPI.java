package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.backend.model.MonthlyRevenue;

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
        
        JsonObject resObj = new Gson().fromJson(jsonResponse, JsonObject.class);
        if (resObj != null && "success".equals(resObj.get("status").getAsString())) {
            Type listType = new TypeToken<ArrayList<MonthlyRevenue>>(){}.getType();
            return new Gson().fromJson(resObj.get("data"), listType);
        }
        throw new Exception("Lỗi khi tải dữ liệu báo cáo!");
    }
}
