package quanlykhachsan.backend.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import javax.net.ssl.*;

/** mTLS-only production transport with a 3s total deadline and bounded responses. */
public final class AiServiceClient {
    public static final String MANUAL_VERIFICATION = "AI service unavailable. Switch to manual Front Desk verification.";
    private final HttpClient client;
    private final URI base;
    private final Duration timeout;

    public AiServiceClient(URI base, SSLContext tls) {
        if (!"https".equals(base.getScheme()) || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null || !"".equals(base.getPath()))
            throw new IllegalArgumentException("AI service requires an HTTPS origin");
        this.base = base;
        this.timeout = Duration.ofSeconds(3);
        this.client = HttpClient.newBuilder().sslContext(java.util.Objects.requireNonNull(tls))
                .connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Explicit test transport; production configuration never accepts HTTP. */
    AiServiceClient(URI base, HttpClient client, Duration timeout) {
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(3)) > 0)
            throw new IllegalArgumentException("Deadline must be at most three seconds");
        this.base=base; this.client=client; this.timeout=timeout;
    }

    public static AiServiceClient fromEnvironment() throws IOException, GeneralSecurityException {
        char[] password = Files.readString(Path.of(required("AI_TLS_PASSWORD_FILE")), StandardCharsets.UTF_8).strip().toCharArray();
        try {
            KeyStore identity=KeyStore.getInstance("PKCS12");
            try(var input=Files.newInputStream(Path.of(required("AI_TLS_KEYSTORE_FILE")))) { identity.load(input,password); }
            KeyStore trust=KeyStore.getInstance("PKCS12");
            try(var input=Files.newInputStream(Path.of(required("AI_TLS_TRUSTSTORE_FILE")))) { trust.load(input,password); }
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(identity,password);
            var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tm.init(trust);
            var tls=SSLContext.getInstance("TLSv1.3"); tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);
            return new AiServiceClient(URI.create(System.getenv().getOrDefault("AI_SERVICE_URL","https://ai-service:8082")),tls);
        } finally { java.util.Arrays.fill(password,'\0'); }
    }

    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank()) throw new IllegalStateException(name+" is required");
        return value;
    }

    public String post(String path, String json) throws IOException {
        if (!List.of("/ocr","/face-match","/rag/query","/forecast").contains(path))
            throw new IllegalArgumentException("Unsupported AI operation");
        var request=HttpRequest.newBuilder(base.resolve(path)).timeout(timeout)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
        var pending=client.sendAsync(request, info -> new BoundedBody());
        try {
            var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200) throw new IOException(MANUAL_VERIFICATION);
            return new String(response.body(),StandardCharsets.UTF_8);
        } catch(InterruptedException e) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw new IOException(MANUAL_VERIFICATION,e);
        } catch(ExecutionException | TimeoutException e) {
            pending.cancel(true); throw new IOException(MANUAL_VERIFICATION,e);
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> items){
            for(var item:items){
                if(item.remaining()>65_536-bytes.size()){
                    subscription.cancel();body.completeExceptionally(new IOException("AI response too large"));return;
                }
                byte[] chunk=new byte[item.remaining()];item.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error){body.completeExceptionally(error);}
        public void onComplete(){body.complete(bytes.toByteArray());}
    }
}
