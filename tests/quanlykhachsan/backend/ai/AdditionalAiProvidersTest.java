package quanlykhachsan.backend.ai;

import com.google.gson.*;
import java.io.*;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AdditionalAiProvidersTest {
    @TempDir Path directory;
    private static final String KEY = "synthetic-provider-key-only";
    private static final String CLAUDE = "{\"type\":\"message\",\"role\":\"assistant\",\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"private thought\"},{\"type\":\"text\",\"text\":\"Synthetic answer\"}]}";
    private static final String GROQ = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"reasoning\":\"private thought\",\"content\":\"Synthetic answer\"}}]}";
    private static String body(HttpRequest request) throws IOException {
        CompletableFuture<String> result = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] value = new byte[buffer.remaining()]; buffer.get(value); bytes.writeBytes(value); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        try { return result.get(2, TimeUnit.SECONDS); } catch (Exception e) { throw new IOException(e); }
    }
    private JsonObject config(String provider, String model, String key) {
        JsonObject value = new JsonObject(); value.addProperty("provider", provider); value.addProperty("model", model); value.addProperty("api_key", key); value.addProperty("enabled", true); value.addProperty("knowledge", "Synthetic hotel information"); return value;
    }
    @Test void groqPersistsNamespacedModelsWithoutReusingAnotherProviderCredential() throws Exception {
        var settings = new AiProviderSettings(directory);
        settings.update(config("groq", "developer/synthetic-model", KEY));
        assertEquals("groq", settings.metadata().get("provider"));
        assertEquals("developer/synthetic-model", new AiProviderSettings(directory).read().model());
        assertThrows(IllegalArgumentException.class, () -> settings.update(config("claude", "synthetic-model", "")));
        assertFalse(new Gson().toJson(settings.metadata()).contains(KEY));
        assertEquals("groq", settings.read().provider());
    }
    @Test void claudeWorkspaceIsEncryptedValidatedAndRetainedByOlderClients() throws Exception {
        var settings = new AiProviderSettings(directory);
        var value = config("claude", "claude-synthetic-model", KEY); value.addProperty("workspace_id", "wrkspc_synthetic123");
        settings.update(value);
        settings.update(config("claude", "claude-synthetic-model", ""));
        assertEquals("wrkspc_synthetic123", new AiProviderSettings(directory).read().workspaceId());
        value.addProperty("workspace_id", "invalid\r\nheader");
        assertThrows(IllegalArgumentException.class, () -> settings.update(value));
    }
    @Test void groqRequestsUseItsOwnHostAndChatSchemaWithNoSecretInTheBody() throws Exception {
        var cloud = new CloudAiClient(request -> {
            assertEquals("https://api.groq.com/openai/v1/chat/completions", request.uri().toString());
            assertEquals("Bearer " + KEY, request.headers().firstValue("Authorization").orElseThrow());
            assertTrue(request.headers().firstValue("anthropic-version").isEmpty());
            assertTrue(request.headers().firstValue("x-goog-api-key").isEmpty());
            String json = body(request); assertFalse(json.contains(KEY));
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            assertEquals("developer/synthetic-model", payload.get("model").getAsString());
            assertEquals(1024, payload.get("max_completion_tokens").getAsInt());
            assertEquals("system", payload.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
            assertEquals("Question", payload.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
            return GROQ;
        });
        assertEquals("Synthetic answer", cloud.answer(new AiProviderSettings.Configuration("groq", "developer/synthetic-model", KEY, true, "Hotel facts"), "Question", false));
    }
    @Test void claudeRequestsSendVersionWorkspaceAndSystemInstructionsSeparately() throws Exception {
        var cloud = new CloudAiClient(request -> {
            assertEquals("https://api.anthropic.com/v1/messages", request.uri().toString());
            assertEquals("Bearer " + KEY, request.headers().firstValue("Authorization").orElseThrow());
            assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElseThrow());
            assertEquals("wrkspc_synthetic123", request.headers().firstValue("anthropic-workspace-id").orElseThrow());
            String json = body(request); assertFalse(json.contains(KEY));
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            assertEquals(1024, payload.get("max_tokens").getAsInt());
            assertTrue(payload.get("system").getAsString().contains("Hotel facts"));
            assertEquals("user", payload.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
            return CLAUDE;
        });
        assertEquals("Synthetic answer", cloud.answer(new AiProviderSettings.Configuration("claude", "claude-synthetic-model", KEY, true, "Hotel facts", "wrkspc_synthetic123"), "Question", false));
    }
    @Test void truncatedToolAndEmptyResponsesDoNotBecomeAnswers() {
        for (String stop : java.util.List.of("max_tokens", "tool_use", "refusal", "pause_turn")) {
            var client = new CloudAiClient(request -> CLAUDE.replace("end_turn", stop));
            assertThrows(IOException.class, () -> client.answer(new AiProviderSettings.Configuration("claude", "test", KEY, true, ""), "Question", false));
        }
        for (String json : java.util.List.of("{}", GROQ.replace("\"stop\"", "\"length\""), GROQ.replace("Synthetic answer", ""))) {
            var client = new CloudAiClient(request -> json);
            assertThrows(IOException.class, () -> client.answer(new AiProviderSettings.Configuration("groq", "test", KEY, true, ""), "Question", false));
        }
    }
    @Test void claudeBillingAndAuthenticationErrorsDoNotExposeUpstreamSecrets() {
        var billing = new CloudAiClient.ProviderException(400, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Your credit balance is too low: synthetic-private-data\"}}");
        assertTrue(billing.publicMessage().contains("số dư")); assertFalse(billing.publicMessage().contains("synthetic-private"));
        var auth = new CloudAiClient.ProviderException(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"synthetic-private-data\"}}");
        assertTrue(auth.publicMessage().contains("Anthropic")); assertFalse(auth.publicMessage().contains("synthetic-private"));
    }
}
