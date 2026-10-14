package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RouteEntry represents a single HTTP endpoint mapping with path-variable extraction (TASK-A1.2 / TASK-A3.3).
 * <p>
 * Implements {@link Comparable} to enforce specificity-based prioritization:
 * <ul>
 *   <li>Static routes (0 path variables) strictly take precedence over dynamic routes, preventing route shadowing.</li>
 *   <li>Dynamic routes with fewer variables and longer literal prefixes take precedence over broader wildcards.</li>
 *   <li>Pre-compiles regex patterns once at route registration time to achieve sub-0.5ms dispatch performance.</li>
 * </ul>
 */
public class RouteEntry implements Comparable<RouteEntry> {

    @FunctionalInterface
    public interface RouteHandler {
        void handle(HttpExchange exchange, Map<String, String> pathParams) throws Exception;
    }

    private final String method;
    private final String rawPattern;
    private final Pattern regexPattern;
    private final List<String> pathVariableNames;
    private final RouteHandler handler;
    private final int variableCount;
    private final int literalLength;

    public RouteEntry(String method, String pathPattern, RouteHandler handler) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("HTTP method cannot be null or blank");
        }
        if (pathPattern == null || pathPattern.isBlank()) {
            throw new IllegalArgumentException("Path pattern cannot be null or blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("RouteHandler cannot be null");
        }

        this.method = method.trim().toUpperCase();
        String normalizedPattern = pathPattern.trim();
        if (normalizedPattern.length() > 1 && normalizedPattern.endsWith("/")) {
            normalizedPattern = normalizedPattern.substring(0, normalizedPattern.length() - 1);
        }
        this.rawPattern = normalizedPattern;
        this.handler = handler;
        this.pathVariableNames = new ArrayList<>();
        this.regexPattern = compileRegexPattern(this.rawPattern, this.pathVariableNames);
        this.variableCount = this.pathVariableNames.size();

        // Calculate total length of static literal characters
        int literalLen = 0;
        int last = 0;
        Matcher m = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}").matcher(rawPattern);
        while (m.find()) {
            literalLen += (m.start() - last);
            last = m.end();
        }
        literalLen += (rawPattern.length() - last);
        this.literalLength = literalLen;
    }

    private static Pattern compileRegexPattern(String pattern, List<String> variableNames) {
        Pattern varPattern = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}");
        Matcher matcher = varPattern.matcher(pattern);

        StringBuilder regexBuilder = new StringBuilder("^");
        int lastEnd = 0;
        while (matcher.find()) {
            String literal = pattern.substring(lastEnd, matcher.start());
            regexBuilder.append(escapeRegexLiterals(literal));
            String varName = matcher.group(1);
            variableNames.add(varName);
            // Replace {varName} with standard capturing group ([^/]+) for high-performance indexed extraction
            regexBuilder.append("([^/]+)");
            lastEnd = matcher.end();
        }
        String trailingLiteral = pattern.substring(lastEnd);
        regexBuilder.append(escapeRegexLiterals(trailingLiteral));
        regexBuilder.append("$");

        return Pattern.compile(regexBuilder.toString());
    }

    private static String escapeRegexLiterals(String literal) {
        if (literal == null || literal.isEmpty()) {
            return "";
        }
        return literal.replaceAll("([\\\\.^$*+?()\\[\\]{}|])", "\\\\$1");
    }

    public boolean matchesPath(String requestPath) {
        if (requestPath == null) return false;
        String normalized = requestPath;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (isStatic()) {
            return rawPattern.equals(normalized);
        }
        return regexPattern.matcher(normalized).matches();
    }

    public boolean matchesMethod(String requestMethod) {
        if (requestMethod == null) return false;
        return this.method.equalsIgnoreCase(requestMethod.trim());
    }

    public Map<String, String> extractPathParams(String requestPath) {
        if (requestPath == null || isStatic()) return Collections.emptyMap();
        String normalized = requestPath;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        Matcher matcher = regexPattern.matcher(normalized);
        if (!matcher.matches()) return Collections.emptyMap();

        Map<String, String> params = new LinkedHashMap<>(pathVariableNames.size());
        for (int i = 0; i < pathVariableNames.size(); i++) {
            params.put(pathVariableNames.get(i), matcher.group(i + 1));
        }
        return Collections.unmodifiableMap(params);
    }

    public boolean isStatic() {
        return variableCount == 0;
    }

    public int getVariableCount() {
        return variableCount;
    }

    public int getLiteralLength() {
        return literalLength;
    }

    @Override
    public int compareTo(RouteEntry other) {
        // 1. Static routes strictly precede dynamic routes
        if (this.isStatic() && !other.isStatic()) return -1;
        if (!this.isStatic() && other.isStatic()) return 1;

        // 2. Fewer variables first
        if (this.variableCount != other.variableCount) {
            return Integer.compare(this.variableCount, other.variableCount);
        }

        // 3. Higher literal length (more specific prefix) first
        if (this.literalLength != other.literalLength) {
            return Integer.compare(other.literalLength, this.literalLength);
        }

        return this.rawPattern.compareTo(other.rawPattern);
    }

    public String getMethod() { return method; }
    public String getRawPattern() { return rawPattern; }
    public String getPathPattern() { return rawPattern; }
    public Pattern getRegexPattern() { return regexPattern; }
    public Pattern getCompiledRegex() { return regexPattern; }
    public List<String> getPathVariableNames() { return Collections.unmodifiableList(pathVariableNames); }
    public RouteHandler getHandler() { return handler; }
}
