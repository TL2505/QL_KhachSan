package quanlykhachsan.backend.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import quanlykhachsan.backend.utils.ApiResponseUtil;

/** Bounded pre-model validation. Security events contain categories only, never user payloads. */
public final class PromptGuardrailFilter extends Filter {
    @FunctionalInterface public interface ViolationObserver {void rejected(HttpExchange exchange,JsonObject body) throws IOException;}
    private final ViolationObserver observer;
    public PromptGuardrailFilter(){this((exchange,body)->{});}
    public PromptGuardrailFilter(ViolationObserver observer){this.observer=observer;}
    private static final int MAX_BODY = 16_384;
    private static final Pattern ATTACK = Pattern.compile(
            "ignore.{0,40}(previous|prior|rules|instructions)|system\\s*(override|prompt)|" +
            "override.{0,40}(system|rules|instructions)|bo qua.{0,40}(chi dan|quy tac|huong dan)|" +
            "union\\s+(all\\s+)?select|or\\s+['\"]?1['\"]?\\s*=\\s*['\"]?1|" +
            "(dump|extract|reveal|print|show|display|repeat|hien thi|tiet lo).{0,80}" +
            "(cccd|password|mat khau|system|configuration|secret|api.key|guest.data|pii)|" +
            "(you are now|act as|pretend to be|developer mode|jailbreak|dan mode)|" +
            "(<\\|?(system|im_start)|\\[/?inst\\]|###\\s*(system|instruction))|" +
            "(decode|execute|follow).{0,40}(base64|encoded|hidden).{0,40}(instruction|command|prompt)");

    private static final java.util.List<Pattern> BLOCKED_PATTERNS = java.util.List.of(
            Pattern.compile("\\b(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|rules)\\b"),
            Pattern.compile("\\b(system\\s+override|developer\\s+message|reveal\\s+(the\\s+)?system\\s+prompt)\\b"),
            Pattern.compile("\\b(union\\s+select|or\\s+1\\s*=\\s*1|drop\\s+table|delete\\s+from)\\b"),
            Pattern.compile("\\b(dump|extract|show|display|reveal)\\s+.{0,30}\\b(passwords?|cccd|identity\\s+cards?|pii|personal\\s+data)\\b"),
            Pattern.compile("\\b(dump|extract|show|display|reveal|list)\\s+.{0,50}\\b(other\\s+guests?|guest\\s+records?|customer\\s+records?|credit\\s+cards?|phone\\s+numbers?|email\\s+addresses?)\\b"),
            Pattern.compile("\\b(bypass|disable|override)\\s+.{0,30}\\b(security|guardrails?|authentication|authorization)\\b"),
            Pattern.compile("\\b(bo\\s+qua|hay\\s+bo\\s+qua)\\s+(cac\\s+)?(chi\\s+dan|quy\\s+tag|huong\\s+dan)\\b"),
            Pattern.compile("\\b(hien\\s+thi|trich\\s+xuat|dump|liet\\s+ke)\\s+.{0,30}\\b(mat\\s+khau|cccd|du\\s+lieu\\s+ca\\s+nhan|thong\\s+tin\\s+khach\\s+hang\\s+khac)\\b")
    );

    public static boolean isBlocked(String message) {
        if (message == null || message.isBlank() || message.length() > 4000) return true;
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[\\p{Cf}\\p{Cc}]", " ").toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        if (ATTACK.matcher(normalized).find()) return true;
        String nfd = Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9=*!]+", " ")
                .trim();
        return BLOCKED_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(nfd).find());
    }

    public boolean isUnsafe(String input) {
        return isBlocked(input);
    }

    @Override public String description() { return "Pre-LLM bounded prompt security validation"; }
    @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        if(!"POST".equals(exchange.getRequestMethod())){chain.doFilter(exchange);return;}
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            ApiResponseUtil.sendError(exchange, 413, "ERR_413_PAYLOAD_TOO_LARGE", "Request too large"); return;
        }
        final String message;
        final JsonObject json;
        try {
            json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("message") || !json.get("message").isJsonPrimitive()
                    || !json.getAsJsonPrimitive("message").isString()) throw new IllegalArgumentException();
            message = json.get("message").getAsString();
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException e) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "A message string is required"); return;
        }
        if (isBlocked(message)) {
            observer.rejected(exchange,json);
            org.slf4j.LoggerFactory.getLogger(PromptGuardrailFilter.class).warn("AI security event: PROMPT_INJECTION_DETECTED");
            ApiResponseUtil.sendError(exchange, 400, "PROMPT_INJECTION_DETECTED", "Yêu cầu không hợp lệ do vi phạm chính sách bảo mật."); return;
        }
        exchange.setStreams(new ByteArrayInputStream(body), null);
        chain.doFilter(exchange);
    }
}
