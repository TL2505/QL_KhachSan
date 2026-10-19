package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.hotelservice.ServiceOrderController;

public final class ExpressPassController implements HttpHandler {
    public void handle(HttpExchange e)throws IOException {
        e.getResponseHeaders().set("Cache-Control","no-store");
        try {
            String role=String.valueOf(e.getAttribute("role")).toLowerCase(Locale.ROOT);
            boolean staff=Set.of("admin","front_desk","staff","manager","gm").contains(role);
            var service=ExpressPassService.configured();
            boolean verify=e.getRequestURI().getPath().equals("/api/v1/checkin-passes/verify");
            if(verify&&!staff)throw new SecurityException();
            if((verify&&!e.getRequestMethod().equals("POST"))||(!verify&&!e.getRequestMethod().equals("GET"))){ApiResponseUtil.sendError(e,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed");return;}
            var claims=verify?service.verify(PortalValidation.text(PortalValidation.body(e),"token",512,true)):null;
            int booking=verify?claims.bookingId():Integer.parseInt(PortalValidation.query(e).get("booking_id"));
            try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT customer_id,room_id,status FROM bookings WHERE id=?")){
                q.setInt(1,booking);try(var r=q.executeQuery()){
                    if(!r.next()){ApiResponseUtil.sendError(e,404,"ERR_404_NOT_FOUND","Booking not found");return;}
                    int customer=r.getInt(1),room=r.getInt(2);
                    if(!staff&&ServiceOrderController.resolveCustomerId(e)!=customer)throw new SecurityException();
                    if(!"confirmed".equals(r.getString(3)))throw new IllegalStateException("A confirmed booking is required");
                    if(verify){if(customer!=claims.customerId()||room!=claims.roomId())throw new IllegalArgumentException();
                        ApiResponseUtil.sendSuccess(e,Map.of("verified",true,"booking_id",booking,"room_id",room,"requires_staff_identity_check",true));
                    }else{String token=service.issue(booking,customer,room);ApiResponseUtil.sendSuccess(e,Map.of("token",token,"qr_svg",service.svg(token),"expires_in_seconds",900));}
                }
            }
        }catch(SecurityException ex){ApiResponseUtil.sendError(e,403,"ERR_403_FORBIDDEN","Pass access denied");}
        catch(IllegalArgumentException|NullPointerException ex){ApiResponseUtil.sendError(e,400,"ERR_400_BAD_REQUEST","Invalid or expired pass request");}
        catch(IllegalStateException ex){ApiResponseUtil.sendError(e,409,"ERR_409_CONFLICT",ex.getMessage());}
        catch(SQLException ex){ApiResponseUtil.sendError(e,503,"ERR_503_UNAVAILABLE","Pass verification unavailable");}
    }
}
