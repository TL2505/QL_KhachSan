package quanlykhachsan.stubs;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.HmacSignatureUtil;

/** Configurable, signed test dispatcher; caller controls target and transaction fixture. */
public final class BankWebhookSimulator {
    public HttpResponse<String> fire(URI backend,String secret,JsonObject payload)throws Exception {
        if(!java.util.Set.of("localhost","127.0.0.1","::1").contains(backend.getHost()))throw new IllegalArgumentException("Test dispatcher targets localhost only");
        String body=payload.toString();String signature=HmacSignatureUtil.sign(secret,body.getBytes(StandardCharsets.UTF_8));
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().send(HttpRequest.newBuilder(backend.resolve("/api/v1/webhooks/vietqr")).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("X-Signature",signature).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
