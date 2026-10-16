package quanlykhachsan.backend.report;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.time.*;
import java.util.*;
import quanlykhachsan.backend.ai.RevenueMlClient;
import quanlykhachsan.backend.pricing.*;
import quanlykhachsan.backend.utils.*;

public final class YieldRecommendationController implements HttpHandler {
    private final RevenueMlClient ml=new RevenueMlClient();
    private final ForecastCache<String> cache=new ForecastCache<>(Clock.systemUTC());
    @Override public void handle(HttpExchange e) throws IOException {
        String role=String.valueOf(e.getAttribute("role")).toLowerCase(Locale.ROOT);
        if(!Set.of("admin","manager","gm","revenue_manager").contains(role)){ApiResponseUtil.sendError(e,403,"ERR_403_FORBIDDEN","Management access required");return;}
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String path=e.getRequestURI().getPath();
        try{
            if(path.equals("/api/v1/revenue/forecast")&&e.getRequestMethod().equals("GET")){
                int days=Integer.parseInt(PortalValidation.query(e).getOrDefault("days","30"));
                if(days<1||days>90)throw new IllegalArgumentException("days must be within [1,90]");
                String data=cache.get(today+"|"+days,()->approvalContext(load(today,days),today,days));
                JsonArray rows=JsonParser.parseString(data).getAsJsonArray();
                for(var item:rows){var row=item.getAsJsonObject();var interval=row.get("confidence_interval");row.add("confidence_lower",interval.isJsonArray()?interval.getAsJsonArray().get(0):JsonNull.INSTANCE);row.add("confidence_upper",interval.isJsonArray()?interval.getAsJsonArray().get(1):JsonNull.INSTANCE);row.add("recommended_bar",JsonNull.INSTANCE);}
                if("array".equals(PortalValidation.query(e).get("format")))ApiResponseUtil.sendSuccess(e,200,rows);
                else{var result=new JsonObject();boolean model=rows.get(0).getAsJsonObject().get("source").getAsString().equals("ML");result.addProperty("model",model?"Prophet-Additive-VietnamHolidays":"RULE_BASED_ON_BOOKS");result.add("generated_at",rows.get(0).getAsJsonObject().get("generated_at"));result.add("forecast",rows);ApiResponseUtil.sendSuccess(e,200,result);}return;
            }
            if(path.equals("/api/v1/revenue/apply-rates")&&e.getRequestMethod().equals("POST")){
                if(role.equals("revenue_manager")){ApiResponseUtil.sendError(e,403,"ERR_403_FORBIDDEN","Management approval required");return;}
                byte[] bytes=e.getRequestBody().readNBytes(262_145);if(bytes.length>262_144){ApiResponseUtil.sendError(e,413,"ERR_413_PAYLOAD_TOO_LARGE","Rate batch too large");return;}
                JsonObject body=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if(body.has("rates")&&body.get("rates").isJsonArray())for(var item:body.getAsJsonArray("rates")){var rate=item.getAsJsonObject();if(rate.has("approved_multiplier")&&rate.has("multiplier")&&rate.get("approved_multiplier").getAsBigDecimal().compareTo(rate.get("multiplier").getAsBigDecimal())!=0)throw new IllegalArgumentException();}
                var rates=JsonUtil.getGson().fromJson(body.get("rates"),RateApprovalService.Rate[].class);
                if(body.has("preview")&&body.get("preview").getAsBoolean()){
                    if(rates==null||rates.length<1||rates.length>1000)throw new IllegalArgumentException();
                    for(var rate:rates)YieldRatePolicy.approve(rate.multiplier(),true,rate.override_reason());
                    ApiResponseUtil.sendSuccess(e,Map.of("available",false,"reason","FORECAST_NOT_CATEGORY_SPECIFIC","message","Chưa có công suất dự báo theo từng hạng phòng để loại trừ booking đã chốt; doanh thu tham khảo chưa khả dụng."));return;
                }
                int actor=Integer.parseInt(String.valueOf(e.getAttribute("userId")));
                try(var c=DatabaseConnection.getConnection()){new RateApprovalService().apply(c,rates==null?null:Arrays.asList(rates),actor,true,today);}
                cache.clear();ApiResponseUtil.sendSuccess(e,200,Map.of("applied",rates.length,"updated_count",rates.length,"message","Rates approved"));return;
            }
            boolean known=Set.of("/api/v1/revenue/forecast","/api/v1/revenue/apply-rates").contains(path);
            ApiResponseUtil.sendError(e,known?405:404,known?"ERR_405_METHOD_NOT_ALLOWED":"ERR_404_NOT_FOUND","Route or method not found");
        }catch(JsonParseException | IllegalArgumentException | java.time.DateTimeException | NullPointerException ex){ApiResponseUtil.sendError(e,400,"ERR_400_BAD_REQUEST","Invalid forecast or approval request");}
        catch(SecurityException ex){ApiResponseUtil.sendError(e,403,"ERR_403_FORBIDDEN",ex.getMessage());}
        catch(IllegalStateException ex){ApiResponseUtil.sendError(e,409,"ERR_409_CONFLICT","Rates changed; refresh before approving");}
        catch(SQLException ex){ApiResponseUtil.sendError(e,503,"ERR_503_REVENUE_UNAVAILABLE","Revenue data unavailable");}
        catch(RevenueUnavailable ex){ApiResponseUtil.sendError(e,503,"ERR_503_REVENUE_UNAVAILABLE","Revenue data unavailable");}
    }
    private String load(LocalDate start,int days){
        try{
            JsonArray result=ml.forecast(start,days);
            JsonArray clean=new JsonArray();
            if(result.size()!=days)throw new IllegalArgumentException();
            for(int i=0;i<days;i++){
                var row=result.get(i).getAsJsonObject();
                if(!row.get("date").getAsString().equals(start.plusDays(i).toString()))throw new IllegalArgumentException();
                double occupancy=row.get("predicted_occupancy").getAsDouble();
                if(!Double.isFinite(occupancy)||occupancy<0||occupancy>100)throw new IllegalArgumentException();
                JsonArray interval=row.getAsJsonArray("confidence_interval");
                if(interval==null||interval.size()!=2)throw new IllegalArgumentException();
                double lower=interval.get(0).getAsDouble(),upper=interval.get(1).getAsDouble();
                if(!Double.isFinite(lower)||!Double.isFinite(upper)||lower<0||upper>100||lower>occupancy||upper<occupancy)throw new IllegalArgumentException();
                JsonObject safe=new JsonObject();safe.addProperty("date",start.plusDays(i).toString());safe.addProperty("predicted_occupancy",occupancy);
                safe.addProperty("recommended_multiplier",YieldRatePolicy.recommendation(row.get("recommended_multiplier").getAsBigDecimal()));safe.add("confidence_interval",interval);safe.addProperty("source","ML");clean.add(safe);
            }return clean.toString();
        }catch(IOException | JsonParseException | IllegalArgumentException | IllegalStateException | NullPointerException ex){
            try(var c=DatabaseConnection.getConnection()){
                int total;
                try(var q=c.createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM rooms WHERE status NOT IN ('out_of_order','out_of_service')")){r.next();total=r.getInt(1);}
                if(total<1)throw new IllegalStateException("No operational inventory");
                JsonArray result=new JsonArray();
                for(int i=0;i<days;i++){
                    LocalDate day=start.plusDays(i);int occupied;
                    try(var q=c.prepareStatement("SELECT COUNT(DISTINCT b.room_id) FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.status IN ('confirmed','checked_in') AND r.status NOT IN ('out_of_order','out_of_service') AND b.check_in_date<? AND b.check_out_date>?")){
                        q.setTimestamp(1,Timestamp.valueOf(day.plusDays(1).atStartOfDay()));q.setTimestamp(2,Timestamp.valueOf(day.atStartOfDay()));try(var r=q.executeQuery()){r.next();occupied=r.getInt(1);}
                    }
                    JsonObject row=new JsonObject();row.addProperty("date",day.toString());row.addProperty("predicted_occupancy",occupied*100.0/total);
                    row.addProperty("recommended_multiplier",PricingTier.forOccupancy(occupied,total).multiplier());row.add("confidence_interval",JsonNull.INSTANCE);row.addProperty("source","RULE_BASED_ON_BOOKS");result.add(row);
                }return result.toString();
            }catch(SQLException e){throw new RevenueUnavailable(e);}
        }
    }
    private String approvalContext(String forecast,LocalDate start,int days){
        JsonArray rows=JsonParser.parseString(forecast).getAsJsonArray();
        try(var c=DatabaseConnection.getConnection()){
            var types=new JsonArray();var versions=new HashMap<String,JsonObject>();
            try(var q=c.createStatement();var r=q.executeQuery("SELECT id,name FROM room_types ORDER BY id")){while(r.next()){var type=new JsonObject();type.addProperty("id",r.getInt(1));type.addProperty("name",r.getString(2));types.add(type);}}
            try(var q=c.prepareStatement("SELECT rate_date,room_type_id,version FROM pricing_rules WHERE rate_date>=? AND rate_date<?")){
                q.setDate(1,java.sql.Date.valueOf(start));q.setDate(2,java.sql.Date.valueOf(start.plusDays(days)));try(var r=q.executeQuery()){while(r.next())versions.computeIfAbsent(r.getDate(1).toString(),key->new JsonObject()).addProperty(Integer.toString(r.getInt(2)),r.getInt(3));}
            }
            for(int i=0;i<rows.size();i++){
                var row=rows.get(i).getAsJsonObject();row.add("approval_versions",versions.getOrDefault(start.plusDays(i).toString(),new JsonObject()));row.addProperty("occupancy_scope","HOTEL_INCLUDING_EXISTING_BOOKINGS");
                try(var q=c.prepareStatement("SELECT occupancy FROM v_daily_occupancy_history WHERE business_date=?")){q.setDate(1,java.sql.Date.valueOf(start.plusDays(i).minusYears(1)));try(var r=q.executeQuery()){if(r.next())row.addProperty("last_year_occupancy",r.getBigDecimal(1));else row.add("last_year_occupancy",JsonNull.INSTANCE);}}
            }
            if(!rows.isEmpty()){rows.get(0).getAsJsonObject().add("room_types",types);rows.get(0).getAsJsonObject().addProperty("generated_at",Instant.now().toString());}return rows.toString();
        }catch(SQLException ex){throw new RevenueUnavailable(ex);}
    }
    private static final class RevenueUnavailable extends RuntimeException {RevenueUnavailable(SQLException e){super(e);}}
}
