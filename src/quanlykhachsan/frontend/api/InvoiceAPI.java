package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Invoice;

public class InvoiceAPI {
    private static final String BASE_URL = "http://localhost:8080/api/invoices";
    private static final Gson gson = new Gson();

    public static List<Invoice> searchInvoices(String keyword) {
        List<Invoice> list = new ArrayList<>();
        try {
            String urlStr = BASE_URL;
            if (keyword != null && !keyword.trim().isEmpty()) {
                urlStr += "?keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8");
            }

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 200) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");
                list = gson.fromJson(reader, new TypeToken<List<Invoice>>() {}.getType());
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
