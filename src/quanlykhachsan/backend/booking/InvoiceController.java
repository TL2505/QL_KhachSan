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
            String path = exchange.getRequestURI().getPath();
            
            if (path.matches("^/api/invoices/\\d+/pdf$")) {
                try {
                    String[] parts = path.split("/");
                    int invoiceId = Integer.parseInt(parts[3]);
                    Invoice invoice = invoiceDAO.getInvoiceById(invoiceId);
                    
                    if (invoice == null) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    
                    byte[] pdfBytes = quanlykhachsan.backend.utils.PdfGenerator.generateInvoicePdf(invoice);
                    
                    exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                    exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"Invoice_" + invoiceId + ".pdf\"");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, pdfBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(pdfBytes);
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String keyword = "";
            String status = "";
            String fromDate = "";
            String toDate = "";
            int page = 1;
            int limit = 10;
            
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        String key = pair.substring(0, idx);
                        String value = pair.substring(idx + 1);
                        try {
                            value = java.net.URLDecoder.decode(value, "UTF-8");
                            if (key.equals("keyword")) keyword = value;
                            else if (key.equals("status")) status = value;
                            else if (key.equals("fromDate")) fromDate = value;
                            else if (key.equals("toDate")) toDate = value;
                            else if (key.equals("page")) page = Integer.parseInt(value);
                            else if (key.equals("limit")) limit = Integer.parseInt(value);
                        } catch (Exception e) {}
                    }
                }
            }
            int offset = (page - 1) * limit;
            if (offset < 0) offset = 0;

            List<Invoice> invoices = invoiceDAO.searchInvoices(keyword, status, fromDate, toDate, offset, limit);

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
