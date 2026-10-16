package quanlykhachsan.backend.ai;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import com.google.gson.*;

public final class RevenueMlClient {
    private volatile AiServiceClient client;
    public JsonArray forecast(LocalDate start,int days) throws IOException {
        if(client==null)synchronized(this){if(client==null)try{client=AiServiceClient.fromEnvironment();}
            catch(GeneralSecurityException | IllegalArgumentException | IllegalStateException e){throw new IOException("Forecast unavailable");}}
        JsonObject request=new JsonObject();request.addProperty("start_date",start.toString());request.addProperty("days",days);
        return JsonParser.parseString(client.post("/forecast",request.toString())).getAsJsonArray();
    }
}
