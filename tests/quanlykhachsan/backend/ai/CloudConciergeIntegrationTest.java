package quanlykhachsan.backend.ai;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.backend.utils.*;

class CloudConciergeIntegrationTest {
    @TempDir Path directory;
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"openai","claude","groq"})
    void adminConfigurationFeedsTheGuestChatAndProviderFailuresCanBeRetried(String provider) throws Exception {
        var settings = new AiProviderSettings(directory);
        AtomicInteger providerCalls = new AtomicInteger();
        var cloud = new CloudAiClient(request -> {
            assertEquals("Bearer synthetic-key-for-integration", request.headers().firstValue("Authorization").orElseThrow());
            if (providerCalls.incrementAndGet() == 1) throw new java.io.IOException("Synthetic provider outage");
            return switch(provider) {
                case "claude" -> "{\"type\":\"message\",\"role\":\"assistant\",\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Synthetic hotel answer\"}]}";
                case "groq" -> "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Synthetic hotel answer\"}}]}";
                default -> "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Synthetic hotel answer\"}]}]}";
            };
        });
        var concierge = new VirtualConciergeController((s,c,id,owner,role)->{throw new java.sql.SQLException("Unexpected escalation");}, event->{}, settings, cloud);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/admin/ai-settings", new AiProviderSettingsController(settings, cloud)).getFilters().add(new JwtFilter());
        server.createContext("/api/v1/ai/concierge", concierge).getFilters().add(new JwtFilter()); server.start();
        var client = HttpClient.newHttpClient();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        String admin = JwtTokenUtil.issueToken(1,"synthetic-admin","admin"), guest = JwtTokenUtil.issueToken(2,"synthetic-guest","customer");
        try {
            String config = "{\"provider\":\"%s\",\"model\":\"synthetic-model\",\"api_key\":\"synthetic-key-for-integration\",\"enabled\":true,\"knowledge\":\"Synthetic facts\"}".formatted(provider);
            var saved = client.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/admin/ai-settings")).header("Authorization","Bearer "+admin).PUT(HttpRequest.BodyPublishers.ofString(config)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200,saved.statusCode());
            for(int attempt=0;attempt<2;attempt++) {
                var chat = client.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/ai/concierge/chat")).header("Authorization","Bearer "+guest).POST(HttpRequest.BodyPublishers.ofString("{\"session_id\":\"synthetic-session\",\"message\":\"What time is check-in?\"}")).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(attempt==0?503:200,chat.statusCode());
                assertFalse(chat.body().contains("synthetic-key-for-integration"));
                if(attempt==1) { assertTrue(chat.body().contains("Synthetic hotel answer")); assertTrue(chat.body().contains("\"generated\":true")); }
            }
            assertEquals(2,providerCalls.get());
        } finally { server.stop(0); concierge.close(); }
    }
}
