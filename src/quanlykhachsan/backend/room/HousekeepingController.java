package quanlykhachsan.backend.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import quanlykhachsan.backend.utils.*;

public final class HousekeepingController implements HttpHandler {
    @Override public void handle(HttpExchange e)throws IOException {
        String role=String.valueOf(e.getAttribute("role"));
        if(!java.util.Set.of("housekeeping","admin").contains(role)){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Housekeeping role required"));return;}
        if(e.getRequestMethod().equals("POST")&&e.getRequestURI().getPath().matches("/api/v1/housekeeping/tasks/[0-9]+/start")){
            long taskId=Long.parseLong(e.getRequestURI().getPath().split("/")[5]);
            try(Connection c=DatabaseConnection.getConnection()){
                c.setAutoCommit(false);
                try(PreparedStatement q=c.prepareStatement("SELECT t.status,r.status,r.ready_not_before FROM cleaning_tasks t JOIN rooms r ON r.id=t.room_id WHERE t.id=? FOR UPDATE")){
                    q.setLong(1,taskId);try(ResultSet row=q.executeQuery()){
                        if(!row.next()){c.rollback();ApiResponseUtil.write(e,404,ApiResponseUtil.error("Cleaning task not found"));return;}
                        Timestamp ready=row.getTimestamp(3);
                        if(!row.getString(1).equals("pending")||!row.getString(2).equals("vacant_dirty")||(ready!=null&&ready.after(new Timestamp(System.currentTimeMillis())))){
                            c.rollback();ApiResponseUtil.write(e,409,ApiResponseUtil.error("Task cannot start until the room is released for cleaning"));return;
                        }
                    }
                }
                try(PreparedStatement q=c.prepareStatement("UPDATE cleaning_tasks SET status='in_progress' WHERE id=? AND status='pending'")){
                    q.setLong(1,taskId);if(q.executeUpdate()!=1)throw new SQLException("Task changed during start");
                }
                c.commit();ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(java.util.Map.of("task_id",taskId,"status","in_progress")));
            }catch(SQLException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Cleaning task could not be started"));}
            return;
        }
        if(e.getRequestMethod().equals("GET")&&e.getRequestURI().getPath().equals("/api/v1/housekeeping/tasks")){
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement(
                    "SELECT t.id,t.booking_id,t.room_id,r.room_number,t.rental_type,t.estimated_duration_minutes,t.status,t.due_at,t.expected_finish_at,t.created_at,"
                  + "(t.due_at IS NOT NULL AND t.due_at<CURRENT_TIMESTAMP(6)) AS overdue,"
                  + "(t.due_at IS NOT NULL AND t.expected_finish_at IS NOT NULL AND t.expected_finish_at>t.due_at) AS turnaround_conflict "
                  + "FROM cleaning_tasks t JOIN rooms r ON r.id=t.room_id WHERE t.status IN ('pending','in_progress') "
                  + "ORDER BY (t.due_at IS NOT NULL AND t.expected_finish_at IS NOT NULL AND t.expected_finish_at>t.due_at) DESC,"
                  + "CASE t.status WHEN 'in_progress' THEN 0 ELSE 1 END,(t.due_at IS NULL),t.due_at,t.created_at")){
                JsonArray tasks=new JsonArray();
                try(ResultSet rows=q.executeQuery()){while(rows.next()){
                    JsonObject task=new JsonObject();task.addProperty("id",rows.getLong("id"));task.addProperty("booking_id",rows.getInt("booking_id"));
                    task.addProperty("room_id",rows.getInt("room_id"));task.addProperty("room_number",rows.getString("room_number"));
                    task.addProperty("rental_type",rows.getString("rental_type"));task.addProperty("estimated_duration_minutes",rows.getInt("estimated_duration_minutes"));
                    task.addProperty("status",rows.getString("status"));Timestamp due=rows.getTimestamp("due_at");if(due==null)task.add("due_at",com.google.gson.JsonNull.INSTANCE);else task.addProperty("due_at",due.toLocalDateTime().toString());
                    task.addProperty("overdue",rows.getBoolean("overdue"));
                    task.addProperty("turnaround_conflict",rows.getBoolean("turnaround_conflict"));
                    Timestamp expected=rows.getTimestamp("expected_finish_at");if(expected==null)task.add("expected_finish_at",com.google.gson.JsonNull.INSTANCE);else task.addProperty("expected_finish_at",expected.toLocalDateTime().toString());
                    task.addProperty("created_at",rows.getTimestamp("created_at").toLocalDateTime().toString());tasks.add(task);
                }}
                ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(tasks));
            }catch(SQLException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Cleaning task list is temporarily unavailable"));}
            return;
        }
        if(!e.getRequestMethod().equals("PUT")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try {
            if(!e.getRequestURI().getPath().matches("/api/v1/rooms/[0-9]+/housekeeping"))throw new IllegalArgumentException();
            int room=Integer.parseInt(e.getRequestURI().getPath().split("/")[4]);var b=PortalValidation.body(e);
            String status=PortalValidation.text(b,"status",30,true);
            if(!java.util.Set.of("vacant_clean","occupied_clean").contains(status))throw new IllegalArgumentException();
            Integer version=b.has("room_version")?PortalValidation.integer(b,"room_version",0):null;
            Integer seconds=b.has("cleaning_duration_seconds")?PortalValidation.integer(b,"cleaning_duration_seconds",0):null;
            if(seconds!=null && seconds>86400)throw new IllegalArgumentException();
            int user=Integer.parseInt(String.valueOf(e.getAttribute("user_id")));
            var change=new RoomStateService.Change(room,version,status,user,role,"Housekeeping inspection",seconds,PortalValidation.text(b,"inspection_notes",255,false),null,null,null);
            IdempotentTransaction.Result result;
            if(e.getRequestHeaders().getFirst("Idempotency-Key")!=null)result=IdempotentTransaction.execute(e,b.toString(),c->new IdempotentTransaction.Result(200,ApiResponseUtil.successWithData(new RoomStateService().change(c,change))));
            else try(Connection c=DatabaseConnection.getConnection()){c.setAutoCommit(false);try{result=new IdempotentTransaction.Result(200,ApiResponseUtil.successWithData(new RoomStateService().change(c,change)));c.commit();}catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}}
            ApiResponseUtil.write(e,result.status(),result.body());
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Role is not authorized"));}
        catch(IllegalArgumentException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid housekeeping data"));}
        catch(OptimisticLockException | IllegalStateException ex){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Room state changed or transition is not permitted"));}
        catch(Exception ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Housekeeping update is temporarily unavailable"));}
    }
}
