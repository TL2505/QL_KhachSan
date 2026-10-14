package quanlykhachsan.backend.booking;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import quanlykhachsan.backend.utils.*;

public final class PaymentPollController implements HttpHandler {
    @Override public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try(Connection c=DatabaseConnection.getConnection()) {
            String[] path=e.getRequestURI().getPath().split("/");
            if(path.length!=6 || !path[5].equals("payment-status"))throw new IllegalArgumentException();
            var f=FolioAccess.find(c,e,path[4]);if(f==null){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Payment reference not found"));return;}
            JsonObject data=new JsonObject();data.addProperty("folio_id",f.id());data.addProperty("folio_code",f.code());data.addProperty("balance",f.balance());data.addProperty("status",f.status());data.addProperty("last_payment_at",f.lastPayment());
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(data));
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Role or guest identity is not authorized"));}
        catch(IllegalArgumentException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid payment reference"));}
        catch(SQLException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Payment status is temporarily unavailable"));}
    }
}
