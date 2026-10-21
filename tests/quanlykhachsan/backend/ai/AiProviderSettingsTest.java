package quanlykhachsan.backend.ai;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.backend.utils.*;

class AiProviderSettingsTest {
    @TempDir Path directory;
    private static final String KEY = "synthetic-ai-provider-credential-only";
    private JsonObject config(String provider, String key) {
        JsonObject body = new JsonObject(); body.addProperty("provider", provider); body.addProperty("model", "synthetic-model");
        body.addProperty("api_key", key); body.addProperty("enabled", true); body.addProperty("knowledge", "Synthetic hotel facts"); return body;
    }
    @Test void encryptedSettingsPersistWithoutDisclosingTheCredential() throws Exception {
        var settings = new AiProviderSettings(directory);
        assertEquals(false, settings.metadata().get("has_key"));
        settings.update(config("openai", KEY));
        assertFalse(new String(Files.readAllBytes(directory.resolve("provider.enc")), java.nio.charset.StandardCharsets.ISO_8859_1).contains(KEY));
        assertFalse(new Gson().toJson(settings.metadata()).contains(KEY));
        assertFalse(settings.read().toString().contains(KEY));
        var reopened = new AiProviderSettings(directory);
        assertEquals(KEY, reopened.read().apiKey());
        reopened.update(config("openai", ""));
        assertEquals(KEY, reopened.read().apiKey());
        reopened.delete(); assertEquals(false, reopened.metadata().get("has_key")); assertFalse(reopened.read().enabled());
    }
    @Test void invalidModelsAndProviderChangesCannotReuseOrOverwriteTheOldKey() throws Exception {
        var settings = new AiProviderSettings(directory);
        settings.update(config("openai", KEY));
        assertThrows(IllegalArgumentException.class, () -> settings.update(config("gemini", "")));
        JsonObject bad = config("openai", KEY); bad.addProperty("model", "../../other-host?key=secret");
        assertThrows(IllegalArgumentException.class, () -> settings.update(bad));
        assertEquals("openai", settings.read().provider());
        byte[] encrypted = Files.readAllBytes(directory.resolve("provider.enc")); encrypted[encrypted.length - 1] ^= 1; Files.write(directory.resolve("provider.enc"), encrypted);
        assertThrows(java.security.GeneralSecurityException.class, settings::read);
    }
    @Test void cloudClientsUseFixedDestinationsAndExcludeSecretsFromUrlsAndRequestBodies() throws Exception {
        for (String provider : List.of("openai", "gemini")) {
            var client = new CloudAiClient(request -> {
                assertEquals("https", request.uri().getScheme());
                assertEquals(provider.equals("openai") ? "api.openai.com" : "generativelanguage.googleapis.com", request.uri().getHost());
                assertFalse(request.uri().toString().contains(KEY));
                assertEquals(provider.equals("openai") ? "Bearer " + KEY : KEY, request.headers().firstValue(provider.equals("openai") ? "Authorization" : "x-goog-api-key").orElseThrow());
                assertEquals(45, request.timeout().orElseThrow().toSeconds());
                return provider.equals("openai") ? "{\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\"},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Synthetic answer\"}]}]}"
                    : "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private reasoning\"},{\"text\":\"Synthetic answer\"}]}}]}";
            });
            assertEquals("Synthetic answer", client.answer(new AiProviderSettings.Configuration(provider, "synthetic-model", KEY, true, "Synthetic facts"), "Question", false));
        }
    }
    @Test void emptyBlockedAndIncompleteCloudResponsesAreNotAnswers() {
        for (String body : List.of("{}", "{\"status\":\"incomplete\",\"output\":[]}", "{\"status\":\"completed\",\"output\":[]}")) {
            var client = new CloudAiClient(request -> body);
            assertThrows(java.io.IOException.class, () -> client.answer(new AiProviderSettings.Configuration("openai", "test", KEY, true, ""), "Question", false));
        }
    }
    @Test void providerErrorsDistinguishBadModelsFromOverloadAndQuota() {
        assertTrue(new CloudAiClient.ProviderException(400).publicMessage().contains("mã model"));
        assertTrue(new CloudAiClient.ProviderException(503).publicMessage().contains("quá tải"));
        assertTrue(new CloudAiClient.ProviderException(429).publicMessage().contains("hạn mức"));
    }
    @Test void googleAuthReasonsAreSafeAndSurviveArrayErrorResponses() {
        String error = "{\"error\":{\"message\":\"synthetic-secret-in-upstream-error\",\"details\":[{\"reason\":\"ACCESS_TOKEN_TYPE_UNSUPPORTED\"}]}}";
        for (String body : List.of(error, "[" + error + "]")) {
            var exception = new CloudAiClient.ProviderException(401, body);
            assertEquals("ACCESS_TOKEN_TYPE_UNSUPPORTED", exception.reason);
            assertTrue(exception.publicMessage().contains("ACCESS_TOKEN_TYPE_UNSUPPORTED"));
            assertFalse(exception.publicMessage().contains("synthetic-secret"));
            assertFalse(exception.getMessage().contains("synthetic-secret"));
        }
        var untrusted = new CloudAiClient.ProviderException(403, "{\"error\":{\"details\":[{\"reason\":\"synthetic-secret-in-upstream-error\"}]}}");
        assertEquals("", untrusted.reason);
        assertFalse(untrusted.publicMessage().contains("synthetic-secret"));
    }
    @Test void settingsRoutesRequireVerifiedAdminTokensAndNeverReturnTheKey() throws Exception {
        var settings = new AiProviderSettings(directory);
        AtomicInteger tests = new AtomicInteger();
        var handler = new AiProviderSettingsController(settings, new CloudAiClient(request -> {
            tests.incrementAndGet(); return "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"OK\"}]}]}";
        }));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/admin/ai-settings", handler).getFilters().add(new JwtFilter()); server.start();
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/admin/ai-settings");
        String admin = JwtTokenUtil.issueToken(1, "synthetic-admin", "admin");
        String guest = JwtTokenUtil.issueToken(2, "synthetic-guest", "customer");
        try {
            assertEquals(401, client.send(HttpRequest.newBuilder(uri).header("X-User-Role", "admin").GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            for (String method : List.of("GET", "PUT", "DELETE")) {
                var denied = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + guest).header("X-User-Role", "admin").method(method, HttpRequest.BodyPublishers.ofString(config("openai", KEY).toString())).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(403, denied.statusCode());
            }
            var saved = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + admin).PUT(HttpRequest.BodyPublishers.ofString(config("openai", KEY).toString())).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, saved.statusCode()); assertFalse(saved.body().contains(KEY)); assertEquals("no-store", saved.headers().firstValue("Cache-Control").orElseThrow());
            var tested = client.send(HttpRequest.newBuilder(URI.create(uri + "/test")).header("Authorization", "Bearer " + admin).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, tested.statusCode()); assertEquals(1, tests.get()); assertFalse(tested.body().contains(KEY));
            var deleted = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + admin).DELETE().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, deleted.statusCode()); assertFalse(settings.read().enabled());
        } finally { server.stop(0); }
    }
}
