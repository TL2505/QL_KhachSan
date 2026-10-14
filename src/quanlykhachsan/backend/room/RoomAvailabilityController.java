package quanlykhachsan.backend.room;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import quanlykhachsan.backend.utils.*;

/** Public inventory projection contains no guest, booking or internal staff data. */
public final class RoomAvailabilityController implements HttpHandler {
    @Override public void handle(HttpExchange e) throws IOException {
        if(!e.getRequestURI().getPath().equals("/api/v1/rooms/availability")){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try {
            var query=PortalValidation.query(e);var dates=PortalValidation.range(query.get("check_in"),query.get("check_out"));
            JsonArray rooms=new JsonArray();
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT r.id,r.room_type_id,t.name,r.version,t.base_price FROM rooms r JOIN room_types t ON t.id=r.room_type_id WHERE r.status='vacant_clean' AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id=r.id AND b.status IN ('pending','confirmed','checked_in') AND b.check_in_date<? AND b.check_out_date>?) ORDER BY r.id LIMIT 200")) {
                q.setTimestamp(1,Timestamp.valueOf(dates.checkOut().atTime(12,0)));q.setTimestamp(2,Timestamp.valueOf(dates.checkIn().atTime(14,0)));
                try(ResultSet r=q.executeQuery()){while(r.next()){
                    JsonObject room=new JsonObject();room.addProperty("id",r.getInt(1));room.addProperty("room_type_id",r.getInt(2));room.addProperty("room_type_name",r.getString(3));room.addProperty("version",r.getInt(4));room.addProperty("price",r.getBigDecimal(5));rooms.add(room);
                }}
            }
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(rooms));
        } catch(IllegalArgumentException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid stay dates"));}
        catch(SQLException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Availability is unavailable; please contact the Front Desk"));}
    }
}
