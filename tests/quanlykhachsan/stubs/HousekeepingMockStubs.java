package quanlykhachsan.stubs;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class HousekeepingMockStubs {
    private HousekeepingMockStubs(){ }
    public static boolean handle(HttpExchange e)throws IOException {
        String path=e.getRequestURI().getPath();
        if(!path.matches("/api/v1/rooms/[0-9]+/(housekeeping|minibar|maintenance)"))return false;
        int status=200;String json;
        if(path.endsWith("/minibar") && e.getRequestMethod().equals("GET"))json="{\"status\":\"success\",\"data\":[{\"id\":1,\"name\":\"Heineken (Mock)\",\"unit_price\":50000,\"current_stock\":5},{\"id\":2,\"name\":\"Snack (Mock)\",\"unit_price\":20000,\"current_stock\":5}]}";
        else if(path.endsWith("/housekeeping") && e.getRequestMethod().equals("PUT"))json="{\"status\":\"success\",\"data\":{\"room_id\":101,\"status\":\"vacant_clean\",\"version\":1}}";
        else if((path.endsWith("/minibar") && e.getRequestMethod().equals("POST")) || (path.endsWith("/maintenance") && e.getRequestMethod().equals("PUT"))) {
            String key=e.getRequestHeaders().getFirst("Idempotency-Key");
            if(key==null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")){status=400;json="{\"status\":\"error\",\"message\":\"Idempotency-Key required\"}";}
            else if(path.endsWith("/minibar")){status=201;json="{\"status\":\"success\",\"data\":{\"transaction_id\":999,\"folio_id\":999,\"amount\":120000}}";}
            else {
                JsonObject body;try{body=JsonParser.parseString(new String(e.getRequestBody().readNBytes(16384),StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException ex){body=new JsonObject();}
                if(!body.has("manager_pin") || !body.get("manager_pin").getAsString().equals("246810")){status=409;json="{\"status\":\"error\",\"message\":\"Manager authorization required\"}";}
                else json="{\"status\":\"success\",\"data\":{\"room_id\":101,\"status\":\"out_of_order\",\"version\":1}}";
            }
        } else {status=405;json="{\"status\":\"error\",\"message\":\"Method not allowed\"}";}
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();return true;
    }
}
