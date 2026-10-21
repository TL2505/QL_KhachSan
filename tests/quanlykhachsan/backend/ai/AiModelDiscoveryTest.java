package quanlykhachsan.backend.ai;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.backend.utils.*;

class AiModelDiscoveryTest {
    @TempDir Path directory;
    static final String KEY = "synthetic-model-list-key";
    static JsonObject draft(String provider, String key) {
        var body = new JsonObject(); body.addProperty("provider", provider); body.addProperty("api_key", key); return body;
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"openai","gemini","claude","groq"})
    void modelsUseFixedHostsWithoutCredentialsInTheUrlAndNormalizeIds(String provider) throws Exception {
        var cloud = new CloudAiClient(request -> {
            assertEquals("GET", request.method()); assertTrue(request.bodyPublisher().isEmpty());
            String host = switch(provider) { case "openai" -> "api.openai.com"; case "gemini" -> "generativelanguage.googleapis.com"; case "claude" -> "api.anthropic.com"; default -> "api.groq.com"; };
            assertEquals(host, request.uri().getHost()); assertEquals("https", request.uri().getScheme()); assertFalse(request.uri().toString().contains(KEY));
            assertEquals(provider.equals("gemini") ? KEY : "Bearer " + KEY, request.headers().firstValue(provider.equals("gemini") ? "x-goog-api-key" : "Authorization").orElseThrow());
            if (provider.equals("claude")) { assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElseThrow()); assertEquals("wrkspc_test", request.headers().firstValue("anthropic-workspace-id").orElseThrow()); }
            return provider.equals("gemini") ? "{\"models\":[{\"name\":\"models/synthetic-model\",\"displayName\":\"Synthetic label\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/embedding\",\"supportedGenerationMethods\":[\"embedContent\"]}],\"nextPageToken\":\"next\"}"
                : "{\"data\":[{\"id\":\"synthetic-model\",\"display_name\":\"Synthetic label\"},{\"id\":\"inactive\",\"active\":false},{\"id\":\"../../unsafe?\"}],\"has_more\":false}";
        });
        var list = cloud.models(new AiProviderSettings.Configuration(provider, "", KEY, false, "", "wrkspc_test"));
        assertEquals(1, list.models().size()); assertEquals("synthetic-model", list.models().get(0).id()); assertEquals("Synthetic label", list.models().get(0).name()); assertEquals(provider.equals("gemini"), list.moreAvailable());
    }
    @Test void draftsNeedNoModelAndNeverChangeSavedConfigurationOrReuseAnotherProviderKey() throws Exception {
        var settings = new AiProviderSettings(directory);
        assertEquals(KEY, settings.forModels(draft("groq", KEY)).apiKey());
        assertEquals(false, settings.metadata().get("has_key"));
        var saved = draft("claude", KEY); saved.addProperty("model", "synthetic-model"); saved.addProperty("enabled", true); saved.addProperty("workspace_id", "wrkspc_test"); settings.update(saved);
        assertEquals("wrkspc_test", settings.forModels(draft("claude", "")).workspaceId());
        assertThrows(IllegalArgumentException.class, () -> settings.forModels(draft("groq", "")));
        assertThrows(IllegalArgumentException.class, () -> settings.forModels(draft("http://other-host", KEY)));
        assertEquals("claude", settings.metadata().get("provider"));
        assertEquals("synthetic-model", settings.read().model());
    }
    @Test void adminOnlyCatalogRouteDoesNotSaveKeysOrLeakThem() throws Exception {
        var settings = new AiProviderSettings(directory); var count = new AtomicInteger();
        var cloud = new CloudAiClient(request -> { count.incrementAndGet(); return "{\"data\":[{\"id\":\"developer/synthetic-model\"}]}"; });
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/admin/ai-settings", new AiProviderSettingsController(settings, cloud)).getFilters().add(new JwtFilter()); server.start();
        var client = HttpClient.newHttpClient(); var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/admin/ai-settings/models");
        String admin = JwtTokenUtil.issueToken(1,"synthetic-admin","admin"), guest = JwtTokenUtil.issueToken(2,"synthetic-guest","customer");
        try {
            for (String token : List.of(guest, admin)) {
                var response = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.ofString(draft("groq", KEY).toString())).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(token.equals(admin) ? 200 : 403, response.statusCode()); assertFalse(response.body().contains(KEY));
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                if (token.equals(admin)) assertTrue(response.body().contains("developer/synthetic-model"));
            }
            var missing = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + admin).POST(HttpRequest.BodyPublishers.ofString(draft("groq", "").toString())).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, missing.statusCode()); assertEquals(1, count.get()); assertEquals(false, settings.metadata().get("has_key"));
        } finally { server.stop(0); }
    }
}
