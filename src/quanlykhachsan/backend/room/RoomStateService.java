package quanlykhachsan.backend.room;

import java.sql.*;
import java.time.LocalDateTime;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.SecurityUtil;

public final class RoomStateService {
    public JsonObject change(Connection c,Change change)throws SQLException,OptimisticLockException {
        String from;int version;
        try(PreparedStatement q=c.prepareStatement("SELECT status,version FROM rooms WHERE id=? FOR UPDATE")){q.setInt(1,change.room());try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown room");from=r.getString(1);version=r.getInt(2);}}
        if(change.expectedVersion()!=null && version!=change.expectedVersion())throw new OptimisticLockException();
        new RoomStateMachine().transition(from,change.to(),change.role());
        boolean repair=change.to().equals("out_of_order") || change.to().equals("out_of_service");
        if(repair) {
            if(change.until()==null || !change.until().isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))))throw new IllegalArgumentException("Maintenance finish date required");
            boolean conflict;
            try(PreparedStatement q=c.prepareStatement("SELECT id FROM bookings WHERE room_id=? AND status IN ('pending','confirmed','checked_in') AND check_in_date<? AND check_out_date>CURRENT_TIMESTAMP LIMIT 1 FOR UPDATE")){q.setInt(1,change.room());q.setTimestamp(2,Timestamp.valueOf(change.until()));try(ResultSet r=q.executeQuery()){conflict=r.next();}}
            if(conflict) {
                if(change.managerPin()==null)throw new IllegalStateException("Manager authorization is required for conflicting reservations");
                if(!SecurityUtil.verifyPassword(change.managerPin(),change.managerHash()))throw new SecurityException("Invalid manager authorization");
            }
        } else if(from.equals("out_of_order") || from.equals("out_of_service")) {
            try(PreparedStatement q=c.prepareStatement("SELECT id FROM bookings WHERE room_id=? AND status='checked_in' LIMIT 1 FOR UPDATE")){q.setInt(1,change.room());try(ResultSet r=q.executeQuery()){if(r.next())throw new IllegalStateException("Occupied room requires Front Desk resolution before return to service");}}
        } else if(change.role().equals("front_desk") || change.role().equals("admin")) {
            if(change.to().equals("occupied_clean")) {
                try(PreparedStatement q=c.prepareStatement("SELECT id FROM bookings WHERE room_id=? AND status='checked_in' LIMIT 1 FOR UPDATE")){q.setInt(1,change.room());try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalStateException("Check-in must use the booking workflow");}}
            } else if(change.to().equals("vacant_dirty")) {
                try(PreparedStatement q=c.prepareStatement("SELECT b.id FROM bookings b WHERE b.room_id=? AND b.status='checked_in' LIMIT 1 FOR UPDATE")){q.setInt(1,change.room());try(ResultSet r=q.executeQuery()){if(r.next())throw new IllegalStateException("Check-out must settle folios through the booking workflow");}}
            }
        }
        try(PreparedStatement q=c.prepareStatement("UPDATE rooms SET status=?,version=version+1,maintenance_until=? WHERE id=? AND version=?")){q.setString(1,change.to());q.setTimestamp(2,repair?Timestamp.valueOf(change.until()):null);q.setInt(3,change.room());q.setInt(4,version);if(q.executeUpdate()!=1)throw new OptimisticLockException();}
        try(PreparedStatement q=c.prepareStatement("INSERT INTO room_status_audits(room_id,from_status,to_status,updated_by_user_id,reason,cleaning_duration_seconds,inspection_notes) VALUES(?,?,?,?,?,?,?)")) {
            q.setInt(1,change.room());q.setString(2,from);q.setString(3,change.to());q.setInt(4,change.user());q.setString(5,change.reason());if(change.seconds()==null)q.setNull(6,Types.INTEGER);else q.setInt(6,change.seconds());q.setString(7,change.notes());q.executeUpdate();
        }
        JsonObject data=new JsonObject();data.addProperty("room_id",change.room());data.addProperty("status",change.to());data.addProperty("version",version+1);return data;
    }
    public record Change(int room,Integer expectedVersion,String to,int user,String role,String reason,Integer seconds,String notes,LocalDateTime until,String managerPin,String managerHash) { }
}
