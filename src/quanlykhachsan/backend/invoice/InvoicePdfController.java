package quanlykhachsan.backend.invoice;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.booking.TaxConfig;

public final class InvoicePdfController implements HttpHandler {
    private final VatInvoicePdfGenerator.Hotel configured;
    public InvoicePdfController(){configured=null;}
    public InvoicePdfController(VatInvoicePdfGenerator.Hotel syntheticTestConfiguration){configured=syntheticTestConfiguration;}
    public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestURI().getPath().matches("/api/(v1/)?invoices/[0-9]+/pdf")){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try(Connection c=DatabaseConnection.getConnection()) {
            String[] path=e.getRequestURI().getPath().split("/");long id=Long.parseLong(path[path.length-2]);
            int booking;String issued,guest,company,tax;BigDecimal total,service,vat;
            try(PreparedStatement q=c.prepareStatement("SELECT i.booking_id,i.issue_date,i.status,i.final_total,i.tax_amount,c.id,c.full_name,c.company_name,c.tax_code FROM invoices i JOIN bookings b ON b.id=i.booking_id JOIN customers c ON c.id=b.customer_id WHERE i.id=?")) {
                q.setLong(1,id);try(ResultSet r=q.executeQuery()) {
                    if(!r.next()){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Invoice not found"));return;}
                    String role=String.valueOf(e.getAttribute("role"));
                    if(!Set.of("admin","front_desk").contains(role) && (!(e.getAttribute("customer_id") instanceof Number n) || n.intValue()!=r.getInt(6)))throw new SecurityException();
                    if(!"paid".equals(r.getString(3)))throw new IllegalArgumentException("Invoice must be finalized and paid");
                    booking=r.getInt(1);issued=r.getString(2);total=r.getBigDecimal(4);vat=r.getBigDecimal(5);guest=r.getString(7);company=r.getString(8);tax=r.getString(9);
                }
            }
            List<VatInvoicePdfGenerator.Item> items=new ArrayList<>();service=BigDecimal.ZERO;BigDecimal ledgerVat=BigDecimal.ZERO;
            try(PreparedStatement q=c.prepareStatement("SELECT COALESCE(SUM(balance),0),COUNT(*) FROM folios WHERE booking_id=?")){q.setInt(1,booking);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(2)==0 || r.getBigDecimal(1).signum()!=0)throw new IllegalArgumentException("Invoice booking is not settled");}}
            try(PreparedStatement q=c.prepareStatement("SELECT t.charge_type,SUM(CASE WHEN t.transaction_type='debit' THEN t.amount ELSE -t.amount END) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=? AND t.charge_type NOT IN ('payment','credit_adjustment') GROUP BY t.charge_type ORDER BY t.charge_type")) {
                q.setInt(1,booking);try(ResultSet r=q.executeQuery()){while(r.next()) {
                    String type=r.getString(1);BigDecimal amount=r.getBigDecimal(2);
                    if(type.equals("service_charge"))service=amount;else if(type.equals("vat"))ledgerVat=amount;
                    else items.add(new VatInvoicePdfGenerator.Item(switch(type){case "room_rate"->"Tiền phòng / Room charge";case "minibar"->"Tiền dịch vụ minibar và đồ uống";case "laundry"->"Giặt là / Laundry";default->"Dịch vụ: "+type;},amount));
                }}
            }
            if(items.isEmpty() || ledgerVat.compareTo(vat)!=0)throw new IllegalArgumentException("Finalized invoice and ledger require reconciliation");
            var data=new VatInvoicePdfGenerator.InvoiceData(id,issued,guest,company,tax,items,service,vat,TaxConfig.fromRuntimeConfiguration().getVatRate(),total,configured!=null);
            byte[] pdf=new VatInvoicePdfGenerator().generate(configured==null?VatInvoicePdfGenerator.Hotel.configured():configured,data);
            e.getResponseHeaders().set("Content-Type","application/pdf");e.getResponseHeaders().set("Content-Disposition","inline; filename=\"HD-"+issued.substring(0,4)+"-"+id+".pdf\"");e.getResponseHeaders().set("Cache-Control","private, no-store");
            e.sendResponseHeaders(200,pdf.length);try(var out=e.getResponseBody()){out.write(pdf);}
        }catch(SecurityException x){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Invoice access denied"));}
        catch(IllegalArgumentException x){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Invoice is not ready or does not reconcile"));}
        catch(Exception x){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Invoice output requires configured hotel details and reconciled records"));}
    }
}
