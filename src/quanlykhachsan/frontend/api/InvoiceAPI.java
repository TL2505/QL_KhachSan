package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Invoice;

public class InvoiceAPI {
    private static final Gson gson = JsonUtil.getGson();

    public static List<Invoice> searchInvoices(String keyword) {
        List<Invoice> list = new ArrayList<>();
        try {
            String path = "/invoices";
            if (keyword != null && !keyword.trim().isEmpty()) {
                path += "?keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8");
            }

            String jsonResponse = HttpUtil.sendGet(path);
            if (jsonResponse != null) {
                list = gson.fromJson(jsonResponse, new TypeToken<List<Invoice>>() {}.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
