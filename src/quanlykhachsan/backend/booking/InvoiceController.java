package quanlykhachsan.backend.booking;

import com.google.gson.Gson;
import quanlykhachsan.backend.utils.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


public class InvoiceController implements HttpHandler {
    private final InvoiceDAO invoiceDAO = new InvoiceDAOImpl();
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if(exchange.getRequestURI().getPath().matches("/api/(v1/)?invoices/[0-9]+/pdf")){new quanlykhachsan.backend.invoice.InvoicePdfController().handle(exchange);return;}
        if(exchange.getRequestURI().getPath().matches("/api/v1/invoices/[0-9]+/electronic(?:/.*)?")){handleElectronic(exchange);return;}
        String role = String.valueOf(exchange.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
        // The portal groups reception accounts under STAFF, while some existing
        // deployments retain the more specific front_desk role. Both are
        // operational roles permitted to look up invoices.
        if(!java.util.Set.of("admin","staff","front_desk").contains(role)){quanlykhachsan.backend.utils.ApiResponseUtil.write(exchange,403,quanlykhachsan.backend.utils.ApiResponseUtil.error("Invoice access denied"));return;}
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

    private void handleElectronic(HttpExchange e) throws IOException {
        String path=e.getRequestURI().getPath();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^/api/v1/invoices/(\\d+)/electronic(?:/(\\d+))?(?:/(issue|adjust|sync|cancel|pdf))?$").matcher(path);
        if(!m.matches()){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,404,quanlykhachsan.backend.utils.ApiResponseUtil.error("Not found"));return;}
        String role=String.valueOf(e.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
        boolean staff=java.util.Set.of("admin","staff","front_desk").contains(role);
        long internalId=Long.parseLong(m.group(1));String electronicIdText=m.group(2),action=m.group(3);
        try {
            if("GET".equalsIgnoreCase(e.getRequestMethod())&&"pdf".equals(action)) {
                if(!authorizedForInvoice(e,internalId,staff))throw new SecurityException();
                if(electronicIdText==null)throw new IllegalArgumentException();
                long electronicId=Long.parseLong(electronicIdText);if(!belongs(electronicId,internalId))throw new IllegalArgumentException();
                int actor=e.getAttribute("userId") instanceof Number n?n.intValue():0;
                byte[] pdf=new quanlykhachsan.backend.invoice.ElectronicInvoiceService().download(electronicId,actor);
                e.getResponseHeaders().set("Content-Type","application/pdf");e.getResponseHeaders().set("Content-Disposition","inline; filename=\"EInvoice-"+electronicId+".pdf\"");e.getResponseHeaders().set("Cache-Control","private, no-store");
                e.sendResponseHeaders(200,pdf.length);try(OutputStream out=e.getResponseBody()){out.write(pdf);}return;
            }
            if(!staff)throw new SecurityException();
            if(!"POST".equalsIgnoreCase(e.getRequestMethod())){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,405,quanlykhachsan.backend.utils.ApiResponseUtil.error("Method not allowed"));return;}
            int actor=e.getAttribute("userId") instanceof Number n?n.intValue():0;
            var service=new quanlykhachsan.backend.invoice.ElectronicInvoiceService();
            com.google.gson.JsonObject result;
            if("issue".equals(action)&&electronicIdText==null) result=service.issue(internalId,actor);
            else if("adjust".equals(action)&&electronicIdText==null){
                var body=quanlykhachsan.backend.utils.PortalValidation.body(e);long parent=quanlykhachsan.backend.utils.PortalValidation.integer(body,"parent_einvoice_id",1);
                String type=quanlykhachsan.backend.utils.PortalValidation.text(body,"document_type",20,true),reason=quanlykhachsan.backend.utils.PortalValidation.text(body,"reason",500,true);
                result=service.adjust(internalId,parent,type,reason,actor);
            } else if(electronicIdText!=null&&action!=null){
                long electronicId=Long.parseLong(electronicIdText);if(!belongs(electronicId,internalId))throw new IllegalArgumentException("Electronic invoice does not belong to this internal statement");
                if("sync".equals(action))result=service.sync(electronicId,actor);
                else if("cancel".equals(action)){var body=quanlykhachsan.backend.utils.PortalValidation.body(e);result=service.cancel(electronicId,quanlykhachsan.backend.utils.PortalValidation.text(body,"reason",500,true),actor);}
                else throw new IllegalArgumentException("Unsupported e-invoice operation");
            } else throw new IllegalArgumentException("Unsupported e-invoice operation");
            quanlykhachsan.backend.utils.ApiResponseUtil.write(e,200,quanlykhachsan.backend.utils.ApiResponseUtil.successWithData(result));
        } catch(SecurityException ex){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,403,quanlykhachsan.backend.utils.ApiResponseUtil.error("Invoice access denied"));}
        catch(quanlykhachsan.backend.invoice.ElectronicInvoiceService.Conflict ex){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,409,quanlykhachsan.backend.utils.ApiResponseUtil.error(ex.getMessage()));}
        catch(quanlykhachsan.backend.invoice.ElectronicInvoiceService.ProviderFailure ex){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,ex.uncertain()?409:502,quanlykhachsan.backend.utils.ApiResponseUtil.error(ex.getMessage()));}
        catch(IllegalArgumentException ex){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,400,quanlykhachsan.backend.utils.ApiResponseUtil.error(ex.getMessage()==null?"Invalid request":ex.getMessage()));}
        catch(IllegalStateException ex){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,503,quanlykhachsan.backend.utils.ApiResponseUtil.error("Nhà cung cấp hóa đơn điện tử chưa được cấu hình đầy đủ."));}
        catch(Exception ex){quanlykhachsan.backend.utils.ApiResponseUtil.write(e,503,quanlykhachsan.backend.utils.ApiResponseUtil.error("Không thể hoàn tất thao tác hóa đơn điện tử. Kiểm tra trạng thái đồng bộ trước khi gửi lại."));}
    }

    private static boolean belongs(long electronicId,long internalId)throws java.sql.SQLException {
        try(var c=quanlykhachsan.backend.utils.DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT 1 FROM electronic_invoices WHERE id=? AND internal_invoice_id=?")){q.setLong(1,electronicId);q.setLong(2,internalId);try(var r=q.executeQuery()){return r.next();}}
    }
    private static boolean authorizedForInvoice(com.sun.net.httpserver.HttpExchange e,long internalId,boolean staff)throws java.sql.SQLException {
        if(staff)return true;
        String role=String.valueOf(e.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
        try(var c=quanlykhachsan.backend.utils.DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT b.customer_id FROM invoices i JOIN bookings b ON b.id=i.booking_id WHERE i.id=?")){
            q.setLong(1,internalId);try(var r=q.executeQuery()){if(!r.next())return false;int owner=r.getInt(1);
                if(("guest".equals(role)||"customer".equals(role))&&e.getAttribute("customer_id") instanceof Number n&&n.intValue()==owner)return true;
                if("customer".equals(role)&&e.getAttribute("userId") instanceof Number user){try(var uq=c.prepareStatement("SELECT customer_id FROM users WHERE id=?")){uq.setInt(1,user.intValue());try(var ur=uq.executeQuery()){return ur.next()&&ur.getObject(1)!=null&&ur.getInt(1)==owner;}}}
                return false;
            }
        }
    }
}
