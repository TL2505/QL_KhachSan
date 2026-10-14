package quanlykhachsan.backend.promotion;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.pricing.DynamicPricingEngine;

/** Never accepts a client total or customer ID as pricing authority. */
public final class VoucherValidationController implements HttpHandler {
    public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestURI().getPath().equals("/api/v1/promotions/validate")){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("POST")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try(Connection c=DatabaseConnection.getConnection()) {
            var b=PortalValidation.body(e);String code=PortalValidation.text(b,b.has("voucher_code")?"voucher_code":"code",50,true);
            if(b.has("code") && b.has("voucher_code") && !code.equals(PortalValidation.text(b,"code",50,true)))throw new IllegalArgumentException("Conflicting voucher codes");
            int customer=e.getAttribute("customer_id") instanceof Number n?n.intValue():0;
            int type;PortalValidation.DateRange dates;
            if(b.has("booking_id")) {
                int booking=PortalValidation.integer(b,"booking_id",1);
                try(PreparedStatement q=c.prepareStatement("SELECT b.customer_id,r.room_type_id,DATE(b.check_in_date),DATE(b.check_out_date) FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.id=?")) {
                    q.setInt(1,booking);try(ResultSet r=q.executeQuery()) {
                        if(!r.next())throw new IllegalArgumentException("Unknown booking");
                        String role=String.valueOf(e.getAttribute("role"));
                        if(!java.util.Set.of("admin","front_desk").contains(role) && customer!=r.getInt(1))throw new SecurityException();
                        customer=r.getInt(1);type=r.getInt(2);dates=new PortalValidation.DateRange(r.getDate(3).toLocalDate(),r.getDate(4).toLocalDate());
                    }
                }
                try(PreparedStatement q=c.prepareStatement("SELECT id FROM promotion_redemptions WHERE booking_id=?")){q.setInt(1,booking);try(ResultSet r=q.executeQuery()){if(r.next())throw new IllegalArgumentException("A voucher is already applied");}}
            } else {
                type=PortalValidation.integer(b,"room_type_id",1);
                dates=PortalValidation.range(PortalValidation.text(b,"check_in_date",10,true),PortalValidation.text(b,"check_out_date",10,true));
            }
            var quote=new DynamicPricingEngine().quote(c,type,dates);
            var discount=new VoucherService().evaluate(c,code,customer,type,dates,quote,false);
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(discount));
        }catch(SecurityException x){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Booking access denied"));}
        catch(IllegalArgumentException x){ApiResponseUtil.write(e,400,ApiResponseUtil.error(x.getMessage()));}
        catch(Exception x){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Voucher preview unavailable"));}
    }
}
