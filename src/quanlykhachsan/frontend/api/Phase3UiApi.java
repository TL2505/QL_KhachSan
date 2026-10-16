package quanlykhachsan.frontend.api;

import com.google.gson.*;
import java.util.Map;
import java.util.function.*;
import javax.swing.SwingWorker;

/** Configuration, payload encoding, network I/O and JSON parsing all happen off the EDT. */
public final class Phase3UiApi {
    private Phase3UiApi(){}
    public static SwingWorker<JsonObject,Void> request(String method,String path,Supplier<String> body,Consumer<JsonObject> success,Consumer<Exception> failure){
        var worker=new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground() throws Exception {
                var response=ApiClient.getInstance().sendSync(method,path,body==null?null:body.get(),Map.of());
                if(response.statusCode()<200||response.statusCode()>299)throw new ApiClient.ApiException(response.statusCode(),response.body());
                return JsonParser.parseString(response.body()).getAsJsonObject();
            }
            protected void done(){
                try{success.accept(get());}
                catch(InterruptedException ex){Thread.currentThread().interrupt();failure.accept(ex);}
                catch(java.util.concurrent.ExecutionException ex){failure.accept(ex.getCause() instanceof Exception problem?problem:new java.io.IOException("Request failed"));}
                catch(java.util.concurrent.CancellationException ex){failure.accept(ex);}
            }
        };worker.execute();return worker;
    }
}
