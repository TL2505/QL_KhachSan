package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.io.IOException;
import quanlykhachsan.backend.payment.PaymentStatus;
import quanlykhachsan.backend.utils.*;

/** FolioAccess enforces guest/customer ownership before accessing history. */
public final class PaymentHistoryController implements HttpHandler {
    public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try(var c=DatabaseConnection.getConnection()){
            var f=FolioAccess.find(c,e,e.getRequestURI().getPath().split("/")[4]);
            if(f==null){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Folio not found"));return;}
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(PaymentStatus.history(c,f.id(),null,100,0)));
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Access denied"));}
        catch(Exception ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Payment history unavailable"));}
    }
}
