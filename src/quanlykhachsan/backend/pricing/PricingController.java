package quanlykhachsan.backend.pricing;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import quanlykhachsan.backend.room.RentalType;
import quanlykhachsan.backend.room.StayWindow;
import quanlykhachsan.backend.utils.*;

public final class PricingController implements HttpHandler {
    @Override public void handle(HttpExchange e) throws IOException {
        if(!e.getRequestURI().getPath().equals("/api/v1/pricing/quote")){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try {
            var query=PortalValidation.query(e);int type=Integer.parseInt(query.get("room_type_id"));
            if(type<1)throw new IllegalArgumentException();
            RentalType requested=RentalType.parse(query.get("rental_type"));
            LocalDate in=parseDate(query.get("check_in")),out=parseDate(query.get("check_out"));
            LocalTime inTime=parseTime(query.get("check_in_time"),LocalTime.of(14,0));
            LocalTime outTime=parseTime(query.get("check_out_time"),LocalTime.NOON);
            StayWindow stay=StayWindow.resolve(requested,in.atTime(inTime),out.atTime(outTime));
            var quote=stay.rentalType()==RentalType.DAILY
                    ? PricingQuoteCache.preview(type,new PortalValidation.DateRange(stay.checkIn().toLocalDate(),stay.checkOut().toLocalDate()))
                    : quoteConfiguredRate(type,stay);
            var data=JsonUtil.getGson().toJsonTree(quote).getAsJsonObject();
            data.addProperty("rental_type",stay.rentalType().databaseValue());
            data.addProperty("check_in_date",stay.checkIn().toString());
            data.addProperty("check_out_date",stay.checkOut().toString());
            if(stay.rentalType()!=RentalType.DAILY)data.addProperty("hourly_add_on_units",stay.billableHourlyAddOns());
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(data));
        }catch(IllegalArgumentException | java.time.DateTimeException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid room category or stay dates"));}
        catch(IllegalStateException ex){ApiResponseUtil.write(e,409,ApiResponseUtil.error(ex.getMessage()));}
        catch(SQLException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Pricing is unavailable; please contact the Front Desk"));}
    }

    private static DynamicPricingEngine.Quote quoteConfiguredRate(int roomType,StayWindow stay)throws SQLException {
        try(Connection c=DatabaseConnection.getConnection()){return new DynamicPricingEngine().quote(c,roomType,stay);}
    }

    private static LocalTime parseTime(String value,LocalTime fallback) {
        if(value==null||value.isBlank())return fallback;
        try{return LocalTime.parse(value,DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT));}
        catch(java.time.DateTimeException ex){throw new IllegalArgumentException("Invalid stay time");}
    }

    private static LocalDate parseDate(String value) {
        if(value==null||value.isBlank())throw new IllegalArgumentException("Missing stay date");
        try{return LocalDate.parse(value);}
        catch(java.time.DateTimeException ex){throw new IllegalArgumentException("Invalid stay date");}
    }
}
