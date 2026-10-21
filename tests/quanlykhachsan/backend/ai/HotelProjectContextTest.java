package quanlykhachsan.backend.ai;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HotelProjectContextTest {
    @TempDir Path directory;
    @Test void packagedKnowledgeMatchesPublishedHoursAndDoesNotUseSeedPrices() throws Exception {
        String text = HotelProjectContext.text();
        String homepage = Files.readString(Path.of("web-ui/public/index.html"));
        assertTrue(homepage.contains("Từ 14:00")); assertTrue(homepage.contains("Trước 12:00 trưa"));
        assertTrue(text.contains("Nhận phòng (check-in): từ 14:00"));
        assertTrue(text.contains("Trả phòng (check-out): trước 12:00"));
        assertTrue(text.contains("06:00–22:00")); assertTrue(homepage.contains("Mở cửa 06:00 - 22:00"));
        assertTrue(text.contains("+842438889999")); assertTrue(text.contains("portal.html?view=bookings"));
        assertFalse(text.contains("500.000")); assertFalse(text.contains("1.000.000"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"openai","gemini","claude","groq"})
    void everyProviderGetsProjectContextEvenWhenAdminNotesAreEmpty(String provider) throws Exception {
        var cloud = new CloudAiClient(request -> {
            JsonObject body = GeminiConversationTest.payload(request);
            String instructions = switch(provider) {
                case "openai" -> body.get("instructions").getAsString();
                case "gemini" -> body.getAsJsonObject("systemInstruction").getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
                case "claude" -> body.get("system").getAsString();
                default -> body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
            };
            assertTrue(instructions.contains("14:00")); assertTrue(instructions.contains("12:00"));
            assertTrue(instructions.contains("concierge@aureliahotel.com"));
            assertTrue(instructions.contains("portal.html?view=concierge"));
            assertTrue(instructions.contains("Do not claim you contacted reception"));
            assertFalse(body.toString().contains("synthetic-project-context-key"));
            return switch(provider) {
                case "gemini" -> GeminiConversationTest.RESPONSE;
                case "claude" -> "{\"type\":\"message\",\"role\":\"assistant\",\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Synthetic answer\"}]}";
                case "groq" -> "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Synthetic answer\"}}]}";
                default -> "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Synthetic answer\"}]}]}";
            };
        });
        assertFalse(cloud.answer(new AiProviderSettings.Configuration(provider,"synthetic-model","synthetic-project-context-key",true,""),"Giờ nhận phòng và trả phòng là khi nào?",false).isBlank());
    }
    @Test void projectContextIsSeparateFromEditableNotesAndIsNotSentDuringConnectionTests() throws Exception {
        var settings = new AiProviderSettings(directory);
        var body = JsonParser.parseString("{\"provider\":\"gemini\",\"model\":\"synthetic-model\",\"api_key\":\"synthetic-context-key\",\"enabled\":true,\"knowledge\":\"Extra confirmed hotel information\"}").getAsJsonObject();
        settings.update(body);
        var meta = settings.metadata();
        assertEquals("Extra confirmed hotel information", meta.get("knowledge"));
        var project = (Map<?,?>)meta.get("project_context"); assertTrue(project.get("text").toString().contains("14:00"));
        assertFalse(new Gson().toJson(meta).contains("synthetic-context-key"));
        var cloud = new CloudAiClient(request -> {
            String payload = GeminiConversationTest.payload(request).toString();
            assertFalse(payload.contains("14:00")); assertFalse(payload.contains("Extra confirmed"));
            return GeminiConversationTest.RESPONSE;
        });
        cloud.answer(settings.read(), "OK", true);
    }
}
