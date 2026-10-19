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
            var checkInTime=parseCheckInTime(query.get("check_in_time"));
            var checkOutTime=parseCheckOutTime(query.get("check_out_time"));
            if ("true".equals(query.get("include_status"))) {
                writeStatuses(e, dates.checkIn(), dates.checkOut(), checkInTime, checkOutTime);
                return;
            }
            JsonArray rooms=new JsonArray();
            // For future stays, housekeeping/current occupancy does not make a room
            // unavailable. Only date-overlapping active bookings do. Same-day check-in
            // still requires a clean, vacant room.
            String cleanRoomClause = dates.checkIn().equals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))
                ? " AND r.status='vacant_clean'" : "";
            String sql="SELECT r.id,r.room_type_id,t.name,r.version,r.price,r.room_number,t.description,t.capacity,t.image_url,t.amenities FROM rooms r JOIN room_types t ON t.id=r.room_type_id WHERE 1=1"+cleanRoomClause+" AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id=r.id AND b.status IN ('pending','confirmed','checked_in') AND b.check_in_date<? AND b.check_out_date>?) AND r.status NOT IN ('out_of_order','out_of_service') ORDER BY r.id LIMIT 200";
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement(sql)) {
                q.setTimestamp(1,Timestamp.valueOf(dates.checkOut().atTime(checkOutTime)));q.setTimestamp(2,Timestamp.valueOf(dates.checkIn().atTime(checkInTime)));
                try(ResultSet r=q.executeQuery()){while(r.next()){
                    JsonObject room=new JsonObject();room.addProperty("id",r.getInt(1));room.addProperty("room_type_id",r.getInt(2));room.addProperty("room_type_name",r.getString(3));room.addProperty("version",r.getInt(4));room.addProperty("price",r.getBigDecimal(5));room.addProperty("room_number",r.getString(6));room.addProperty("description",r.getString(7));room.addProperty("capacity",r.getInt(8));room.addProperty("image_url",r.getString(9));room.addProperty("amenities",r.getString(10));rooms.add(room);
                }}
            }
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(rooms));
        } catch(IllegalArgumentException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid stay dates"));}
        catch(SQLException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Availability is unavailable; please contact the Front Desk"));}
    }

    private static java.time.LocalTime parseCheckInTime(String value) {
        if (value == null || value.isBlank()) return java.time.LocalTime.of(14, 0);
        return parseTime(value);
    }

    private static java.time.LocalTime parseCheckOutTime(String value) {
        if (value == null || value.isBlank()) return java.time.LocalTime.of(12, 0);
        return parseTime(value);
    }

    private static java.time.LocalTime parseTime(String value) {
        try {
            return java.time.LocalTime.parse(value,
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException("Invalid stay time");
        }
    }

    /** Read-only stay-specific labels. Keep the existing availability response unchanged. */
    private void writeStatuses(HttpExchange e, java.time.LocalDate checkIn, java.time.LocalDate checkOut, java.time.LocalTime checkInTime, java.time.LocalTime checkOutTime) throws SQLException, IOException {
        String sql = "SELECT r.id,r.room_type_id,r.status,r.room_number,r.price,t.name,t.capacity,t.description,t.image_url,t.amenities,r.version, "
            + "MAX(CASE WHEN b.status='checked_in' THEN 1 ELSE 0 END) AS occupied, "
            + "MAX(CASE WHEN b.status IN ('pending','confirmed') THEN 1 ELSE 0 END) AS reserved "
            + "FROM rooms r JOIN room_types t ON t.id=r.room_type_id LEFT JOIN bookings b ON b.room_id=r.id "
            + "AND b.status IN ('pending','confirmed','checked_in') AND b.check_in_date<? AND b.check_out_date>? "
            + "GROUP BY r.id,r.room_type_id,r.status,r.room_number,r.price,t.name,t.capacity,t.description,t.image_url,t.amenities,r.version ORDER BY r.id";
        boolean today = checkIn.equals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        JsonArray rooms = new JsonArray();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(sql)) {
            q.setTimestamp(1, Timestamp.valueOf(checkOut.atTime(checkOutTime)));
            q.setTimestamp(2, Timestamp.valueOf(checkIn.atTime(checkInTime)));
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    String physical = r.getString("status");
                    String status;
                    if ("out_of_order".equals(physical) || "out_of_service".equals(physical)) status = "maintenance";
                    else if (r.getInt("occupied") != 0) status = "occupied";
                    else if (r.getInt("reserved") != 0) status = "reserved";
                    else if (today && !"vacant_clean".equals(physical)) status = "not_ready";
                    else status = "available";
                    JsonObject room = new JsonObject();
                    room.addProperty("id", r.getInt("id"));
                    room.addProperty("room_type_id", r.getInt("room_type_id"));
                    room.addProperty("room_number", r.getString("room_number"));
                    room.addProperty("price", r.getBigDecimal("price"));
                    room.addProperty("room_type_name", r.getString("name"));
                    room.addProperty("capacity", r.getInt("capacity"));
                    room.addProperty("description", r.getString("description"));
                    room.addProperty("image_url", r.getString("image_url"));
                    room.addProperty("amenities", r.getString("amenities"));
                    room.addProperty("room_operational_status", physical);
                    room.addProperty("version", r.getInt("version"));
                    room.addProperty("availability_status", status);
                    room.addProperty("available", "available".equals(status));
                    rooms.add(room);
                }
            }
        }
        ApiResponseUtil.write(e, 200, ApiResponseUtil.successWithData(rooms));
    }
}
