package quanlykhachsan.backend.pricing;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import quanlykhachsan.backend.utils.*;

public final class PricingController implements HttpHandler {
    @Override public void handle(HttpExchange e) throws IOException {
        if(!e.getRequestURI().getPath().equals("/api/v1/pricing/quote")){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try {
            var query=PortalValidation.query(e);int type=Integer.parseInt(query.get("room_type_id"));
            if(type<1)throw new IllegalArgumentException();
            var range=PortalValidation.range(query.get("check_in"),query.get("check_out"));
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(PricingQuoteCache.preview(type,range)));
        }catch(IllegalArgumentException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid room category or stay dates"));}
        catch(SQLException | IllegalStateException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Pricing is unavailable; please contact the Front Desk"));}
    }
}
