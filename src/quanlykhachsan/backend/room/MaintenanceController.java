package quanlykhachsan.backend.room;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.time.*;
import quanlykhachsan.backend.utils.*;

public final class MaintenanceController implements HttpHandler {
    private final String managerHash;
    public MaintenanceController(){this(System.getenv("MANAGER_PIN_HASH"));}
    public MaintenanceController(String hash){managerHash=hash;}
    @Override public void handle(HttpExchange e)throws IOException {
        String role=String.valueOf(e.getAttribute("role"));
        if(!java.util.Set.of("maintenance","admin").contains(role)){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Maintenance role required"));return;}
        if(!e.getRequestMethod().equals("PUT")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try {
            IdempotentTransaction.key(e);
            if(!e.getRequestURI().getPath().matches("/api/v1/rooms/[0-9]+/maintenance"))throw new IllegalArgumentException();
            int room=Integer.parseInt(e.getRequestURI().getPath().split("/")[4]);var b=PortalValidation.body(e);
            String status=PortalValidation.text(b,"status",30,true);
            if(!java.util.Set.of("out_of_order","out_of_service","vacant_clean").contains(status))throw new IllegalArgumentException();
            Integer version=b.has("room_version")?PortalValidation.integer(b,"room_version",0):null;
            String until=PortalValidation.text(b,"estimated_finish",30,!status.equals("vacant_clean"));
            LocalDateTime finish=until==null?null:LocalDateTime.parse(until);
            String pin=PortalValidation.text(b,"manager_pin",100,false);if(pin==null)pin=e.getRequestHeaders().getFirst("X-Manager-Pin");
            var change=new RoomStateService.Change(room,version,status,Integer.parseInt(String.valueOf(e.getAttribute("user_id"))),role,PortalValidation.text(b,"reason",255,true),null,null,finish,pin,managerHash);
            var result=IdempotentTransaction.execute(e,b.toString(),c->new IdempotentTransaction.Result(200,ApiResponseUtil.successWithData(new RoomStateService().change(c,change))));
            ApiResponseUtil.write(e,result.status(),result.body());
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Role or manager authorization is not valid"));}
        catch(IllegalArgumentException | DateTimeException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid maintenance request or Idempotency-Key"));}
        catch(OptimisticLockException | IllegalStateException ex){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Room has conflicting reservations or requires manager authorization"));}
        catch(Exception ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Maintenance update is temporarily unavailable"));}
    }
}
