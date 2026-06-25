package quanlykhachsan.backend.booking;

import com.google.gson.Gson;
import quanlykhachsan.backend.utils.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import quanlykhachsan.backend.booking.InvoiceDAO;
import quanlykhachsan.backend.booking.InvoiceDAOImpl;
import quanlykhachsan.backend.booking.Invoice;


public class InvoiceController implements HttpHandler {
    private final InvoiceDAO invoiceDAO = new InvoiceDAOImpl();
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {
            String query = exchange.getRequestURI().getQuery();
            String keyword = "";
            if (query != null && query.startsWith("keyword=")) {
                keyword = query.substring(8);
            }

            List<Invoice> invoices = invoiceDAO.searchInvoices(keyword);

            String response = gson.toJson(invoices);
            byte[] responseBytes = response.getBytes("UTF-8");
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
}
