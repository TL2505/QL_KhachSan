package quanlykhachsan.backend.room;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import quanlykhachsan.backend.utils.*;

public final class HousekeepingController implements HttpHandler {
    @Override public void handle(HttpExchange e)throws IOException {
        String role=String.valueOf(e.getAttribute("role"));
        if(!java.util.Set.of("housekeeping","admin").contains(role)){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Housekeeping role required"));return;}
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
