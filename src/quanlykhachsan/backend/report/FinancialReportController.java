package quanlykhachsan.backend.report;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.time.*;
import quanlykhachsan.backend.utils.*;
public final class FinancialReportController implements HttpHandler {
    public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestURI().getPath().equals("/api/v1/reports/financial/dbr")){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!java.util.Set.of("admin","front_desk").contains(String.valueOf(e.getAttribute("role")))){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Financial report access denied"));return;}
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try(Connection c=DatabaseConnection.getConnection()) {
            var query=PortalValidation.query(e);LocalDate date=LocalDate.parse(query.get("date"));
            try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT business_date FROM hotel_business_state WHERE id=1")){if(!r.next() || !date.isBefore(r.getDate(1).toLocalDate()))throw new IllegalStateException();}
            var snapshot=new DbrLedgerAggregator().read(c,date);if(snapshot==null)throw new IllegalStateException();
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(snapshot));
        }catch(DateTimeException|IllegalArgumentException|NullPointerException x){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Use a valid date (yyyy-MM-dd)"));}
        catch(IllegalStateException x){ApiResponseUtil.write(e,409,ApiResponseUtil.error("A reconciled closed-date snapshot is not available"));}
        catch(Exception x){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Financial report unavailable"));}
    }
}
