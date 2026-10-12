package quanlykhachsan.frontend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ApiClientTest {
    @Test
    void delayedResponseDoesNotBlockEdtAndCallbackRunsOnEdt() throws Exception {
        CountDownLatch responseReceived = new CountDownLatch(1);
        AtomicBoolean callbackOnEdt = new AtomicBoolean();
        AtomicReference<String> contentType = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            try {
                Thread.sleep(3000);
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        try {
            ApiClient client = new ApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(3000)).build());
            long started = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> client.get("slow", new ApiCallback() {
                @Override public void onSuccess(java.net.http.HttpResponse<String> response) {
                    callbackOnEdt.set(SwingUtilities.isEventDispatchThread());
                    assertEquals(200, response.statusCode());
                    responseReceived.countDown();
                }
                @Override public void onError(Exception error) {
                    responseReceived.countDown();
                    throw new AssertionError(error);
                }
            }));
            CountDownLatch heartbeat = new CountDownLatch(1);
            SwingUtilities.invokeLater(heartbeat::countDown);
            assertTrue(heartbeat.await(250, TimeUnit.MILLISECONDS), "EDT must process UI work during network delay");
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000);
            assertTrue(responseReceived.await(5, TimeUnit.SECONDS), "async response callback should complete");
            assertTrue(callbackOnEdt.get(), "response callback must return to the EDT");
            assertEquals("application/json", contentType.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void synchronousCompatibilityBridgeRejectsEdtCalls() throws Exception {
        ApiClient client = new ApiClient(URI.create("http://127.0.0.1:1/"), HttpClient.newHttpClient());
        AtomicReference<Throwable> observed = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { client.sendSync("GET", "", null, java.util.Map.of()); }
            catch (Throwable error) { observed.set(error); }
        });
        assertTrue(observed.get() instanceof IllegalStateException,
            "legacy synchronous API facades must fail before starting network I/O on the EDT");
    }
}
