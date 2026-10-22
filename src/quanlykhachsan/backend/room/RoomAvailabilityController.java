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
            var query=PortalValidation.query(e);
            RentalType rentalType=RentalType.parse(query.get("rental_type"));
            java.time.LocalDate checkIn=parseDate(query.get("check_in"));
            java.time.LocalDate checkOut=parseDate(query.get("check_out"));
            var checkInTime=parseCheckInTime(query.get("check_in_time"));
            var checkOutTime=parseCheckOutTime(query.get("check_out_time"));
            StayWindow stay=StayWindow.resolve(rentalType,checkIn.atTime(checkInTime),checkOut.atTime(checkOutTime));
            if ("true".equals(query.get("include_status"))) {
                writeStatuses(e,stay);
                return;
            }
            JsonArray rooms=new JsonArray();
            // A current dirty/occupied state does not erase a later free slot.
            // An unfinished same-day cleaning task does block that slot until its ETA.
            String sql="SELECT r.id,r.room_type_id,t.name,r.version,r.price,r.room_number,t.description,t.capacity,t.image_url,t.image_alt,t.amenities,t.hourly_base_price,t.hourly_extra_hour_price,t.overnight_price FROM rooms r JOIN room_types t ON t.id=r.room_type_id WHERE t.is_visible=1 AND r.status NOT IN ('out_of_order','out_of_service') "
                +"AND NOT (DATE(?)=CURRENT_DATE AND r.status='vacant_dirty' AND NOT EXISTS (SELECT 1 FROM cleaning_tasks ct WHERE ct.room_id=r.id AND ct.status IN ('pending','in_progress') AND COALESCE(ct.expected_finish_at,DATE_ADD(ct.created_at,INTERVAL ct.estimated_duration_minutes MINUTE))<=?)) "
                +"AND NOT EXISTS (SELECT 1 FROM cleaning_tasks ct WHERE ct.room_id=r.id AND ct.status IN ('pending','in_progress') AND COALESCE(ct.expected_finish_at,DATE_ADD(ct.created_at,INTERVAL ct.estimated_duration_minutes MINUTE))>?) "
                +"AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id=r.id AND b.status IN ('pending','confirmed','checked_in','checked_out') AND b.check_in_date<DATE_ADD(?, INTERVAL (CASE ? WHEN 'HOURLY' THEN r.buffer_hourly WHEN 'OVERNIGHT' THEN r.buffer_overnight ELSE r.buffer_daily END) MINUTE) AND b.room_release_at>?) ORDER BY t.display_order,r.id LIMIT 200";
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement(sql)) {
                Timestamp checkInStamp=Timestamp.valueOf(stay.checkIn());
                q.setTimestamp(1,checkInStamp);q.setTimestamp(2,checkInStamp);q.setTimestamp(3,checkInStamp);
                q.setTimestamp(4,Timestamp.valueOf(stay.checkOut()));q.setString(5,stay.rentalType().databaseValue());q.setTimestamp(6,checkInStamp);
                try(ResultSet r=q.executeQuery()){while(r.next()){
                    if(!hasConfiguredRate(r,stay.rentalType()))continue;
                    JsonObject room=new JsonObject();room.addProperty("id",r.getInt(1));room.addProperty("room_type_id",r.getInt(2));room.addProperty("room_type_name",r.getString(3));room.addProperty("version",r.getInt(4));room.addProperty("price",r.getBigDecimal(5));room.addProperty("room_number",r.getString(6));room.addProperty("description",r.getString(7));room.addProperty("capacity",r.getInt(8));room.addProperty("image_url",r.getString(9));room.addProperty("image_alt",r.getString(10));room.addProperty("amenities",r.getString(11));rooms.add(room);
                }}
            }
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(rooms));
        } catch(IllegalArgumentException | java.time.DateTimeException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid stay dates"));}
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

    private static java.time.LocalDate parseDate(String value) {
        if(value==null||value.isBlank())throw new IllegalArgumentException("Missing stay date");
        try{return java.time.LocalDate.parse(value);}
        catch(java.time.DateTimeException ex){throw new IllegalArgumentException("Invalid stay date");}
    }

    /** Read-only stay-specific labels. Keep the existing availability response unchanged. */
    private void writeStatuses(HttpExchange e,StayWindow stay) throws SQLException, IOException {
        String sql = "SELECT r.id,r.room_type_id,r.status,r.room_number,r.price,t.name,t.capacity,t.description,t.image_url,t.image_alt,t.amenities,r.version, "
            + "MAX(CASE WHEN b.status='checked_in' THEN 1 ELSE 0 END) AS occupied, "
            + "MAX(CASE WHEN b.status IN ('pending','confirmed') THEN 1 ELSE 0 END) AS reserved, "
            + "MAX(CASE WHEN b.status='checked_out' THEN 1 ELSE 0 END) AS turnover, "
            + "t.hourly_base_price,t.hourly_extra_hour_price,t.overnight_price, "
            + "EXISTS(SELECT 1 FROM cleaning_tasks ct WHERE ct.room_id=r.id AND ct.status IN ('pending','in_progress') AND COALESCE(ct.expected_finish_at,DATE_ADD(ct.created_at,INTERVAL ct.estimated_duration_minutes MINUTE))<=?) AS task_ready, "
            + "EXISTS(SELECT 1 FROM cleaning_tasks ct WHERE ct.room_id=r.id AND ct.status IN ('pending','in_progress') AND COALESCE(ct.expected_finish_at,DATE_ADD(ct.created_at,INTERVAL ct.estimated_duration_minutes MINUTE))>?) AS task_blocked "
            + "FROM rooms r JOIN room_types t ON t.id=r.room_type_id LEFT JOIN bookings b ON b.room_id=r.id "
            + "AND b.status IN ('pending','confirmed','checked_in','checked_out') "
            + "AND b.check_in_date<DATE_ADD(?, INTERVAL (CASE ? WHEN 'HOURLY' THEN r.buffer_hourly WHEN 'OVERNIGHT' THEN r.buffer_overnight ELSE r.buffer_daily END) MINUTE) AND b.room_release_at>? "
            + "WHERE t.is_visible=1 GROUP BY r.id,r.room_type_id,r.status,r.room_number,r.price,t.name,t.capacity,t.description,t.image_url,t.image_alt,t.amenities,r.version,t.hourly_base_price,t.hourly_extra_hour_price,t.overnight_price,t.display_order ORDER BY t.display_order,r.id";
        boolean today=stay.checkIn().toLocalDate().equals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        JsonArray rooms = new JsonArray();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(sql)) {
                Timestamp checkInStamp=Timestamp.valueOf(stay.checkIn());
                q.setTimestamp(1,checkInStamp);q.setTimestamp(2,checkInStamp);q.setTimestamp(3,Timestamp.valueOf(stay.checkOut()));q.setString(4,stay.rentalType().databaseValue());q.setTimestamp(5,checkInStamp);
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    String physical = r.getString("status");
                    String status;
                    if ("out_of_order".equals(physical) || "out_of_service".equals(physical)) status = "maintenance";
                    else if(!hasConfiguredRate(r,stay.rentalType())) status="pricing_unconfigured";
                    else if (r.getInt("occupied") != 0) status = "occupied";
                    else if (r.getInt("reserved") != 0) status = "reserved";
                    else if (r.getInt("task_blocked") != 0) status = "not_ready";
                    else if (today && (r.getInt("turnover") != 0 || "vacant_dirty".equals(physical)) && r.getInt("task_ready") == 0) status = "not_ready";
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
                    room.addProperty("image_alt", r.getString("image_alt"));
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

    private static boolean hasConfiguredRate(ResultSet row,RentalType type)throws SQLException {
        return switch(type) {
            case DAILY -> true;
            case HOURLY -> row.getBigDecimal("hourly_base_price")!=null&&row.getBigDecimal("hourly_extra_hour_price")!=null;
            case OVERNIGHT -> row.getBigDecimal("overnight_price")!=null&&row.getBigDecimal("hourly_extra_hour_price")!=null;
        };
    }
}
