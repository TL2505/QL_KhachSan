package quanlykhachsan.backend.room;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.*;

public final class MinibarController implements HttpHandler {
    @Override public void handle(HttpExchange e)throws IOException {
        if(!Set.of("admin","front_desk","housekeeping","f&b").contains(String.valueOf(e.getAttribute("role")))){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Minibar role required"));return;}
        try {
            if(!e.getRequestURI().getPath().matches("/api/v1/rooms/[0-9]+/minibar"))throw new IllegalArgumentException();
            int room=Integer.parseInt(e.getRequestURI().getPath().split("/")[4]);
            if(e.getRequestMethod().equals("GET")) {
                JsonArray items=new JsonArray();try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT id,name,unit_price,current_stock,standard_stock FROM minibar_items WHERE room_id=? ORDER BY id")){q.setInt(1,room);try(ResultSet r=q.executeQuery()){while(r.next()){JsonObject item=new JsonObject();item.addProperty("id",r.getInt(1));item.addProperty("name",r.getString(2));item.addProperty("unit_price",r.getBigDecimal(3));item.addProperty("current_stock",r.getInt(4));item.addProperty("standard_stock",r.getInt(5));items.add(item);}}}
                ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(items));return;
            }
            if(!e.getRequestMethod().equals("POST")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
            String key=IdempotentTransaction.key(e);var b=PortalValidation.body(e);List<MinibarPostingService.Item> items=new ArrayList<>();
            if(!b.has("items") || !b.get("items").isJsonArray())throw new IllegalArgumentException();
            for(JsonElement item:b.getAsJsonArray("items")){if(!item.isJsonObject())throw new IllegalArgumentException();items.add(new MinibarPostingService.Item(PortalValidation.integer(item.getAsJsonObject(),"item_id",1),PortalValidation.integer(item.getAsJsonObject(),"quantity",1)));}
            var result=IdempotentTransaction.execute(e,b.toString(),c->new IdempotentTransaction.Result(201,ApiResponseUtil.successWithData(new MinibarPostingService().post(c,room,items,key))));
            ApiResponseUtil.write(e,result.status(),result.body());
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Role is not authorized"));}
        catch(IllegalArgumentException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid minibar items or Idempotency-Key"));}
        catch(IllegalStateException ex){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Room, stock or folio state changed"));}
        catch(Exception ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Minibar posting is temporarily unavailable"));}
    }
}
