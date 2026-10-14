package quanlykhachsan.frontend.api;

import com.google.gson.*;
import java.util.Map;
import java.util.function.Consumer;

/** All network work uses the existing SwingWorker-backed client. */
public final class Phase2UiApi {
    private Phase2UiApi(){ }
    public static void request(String method,String path,String body,String key,Consumer<JsonElement> success,Consumer<Exception> failure) {
        ApiClient.getInstance().request(method,path,body,key==null?Map.of():Map.of("Idempotency-Key",key),new ApiCallback(){
            @Override public void onSuccess(java.net.http.HttpResponse<String> response){try{JsonObject envelope=JsonParser.parseString(response.body()).getAsJsonObject();success.accept(envelope.has("data")?envelope.get("data"):envelope);}catch(RuntimeException ex){failure.accept(ex);}}
            @Override public void onError(Exception error){failure.accept(error);}
        });
    }
}
