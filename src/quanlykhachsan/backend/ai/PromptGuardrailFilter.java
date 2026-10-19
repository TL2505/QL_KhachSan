package quanlykhachsan.backend.ai;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PromptGuardrailFilter {
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("\\b(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|rules)\\b"),
            Pattern.compile("\\b(system\\s+override|developer\\s+message|reveal\\s+(the\\s+)?system\\s+prompt)\\b"),
            Pattern.compile("\\b(union\\s+select|or\\s+1\\s*=\\s*1|drop\\s+table|delete\\s+from)\\b"),
            Pattern.compile("\\b(dump|extract|show|display|reveal)\\s+.{0,30}\\b(passwords?|cccd|identity\\s+cards?|pii|personal\\s+data)\\b"),
            Pattern.compile("\\b(dump|extract|show|display|reveal|list)\\s+.{0,50}\\b(other\\s+guests?|guest\\s+records?|customer\\s+records?|credit\\s+cards?|phone\\s+numbers?|email\\s+addresses?)\\b"),
            Pattern.compile("\\b(bypass|disable|override)\\s+.{0,30}\\b(security|guardrails?|authentication|authorization)\\b"),
            Pattern.compile("\\b(bo\\s+qua|hay\\s+bo\\s+qua)\\s+(cac\\s+)?(chi\\s+dan|quy\\s+tag|huong\\s+dan)\\b"),
            Pattern.compile("\\b(hien\\s+thi|trich\\s+xuat|dump|liet\\s+ke)\\s+.{0,30}\\b(mat\\s+khau|cccd|du\\s+lieu\\s+ca\\s+nhan|thong\\s+tin\\s+khach\\s+hang\\s+khac)\\b")
    );

    public boolean isUnsafe(String input) {
        if (input == null) return true;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9=*!]+", " ")
                .trim();
        return BLOCKED_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}
