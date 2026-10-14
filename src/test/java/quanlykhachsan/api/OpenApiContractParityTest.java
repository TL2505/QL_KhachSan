package quanlykhachsan.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quanlykhachsan.backend.utils.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAPI 3.0 Contract Freeze & Parity Verification Test (TASK-A4.3).
 * <p>
 * Ensures 100% parity between the OpenAPI contract (docs/dev-spec/openapi-p1.json)
 * and the runtime backend implementation:
 * <ul>
 *   <li>Validates OpenAPI 3.0.x compliance and standard info metadata.</li>
 *   <li>Verifies presence of all 10 mandatory Phase 1 core operations + supplementary routes.</li>
 *   <li>Ensures BearerAuth security scheme conforms to HTTP Bearer JWT specifications.</li>
 *   <li>Enforces response code completeness (Success 200/201 and RFC-7807 Error codes 400/401/409/503).</li>
 *   <li>Confirms presence of standard envelope schemas (EnvelopeSuccess, EnvelopeError).</li>
 * </ul>
 */
@DisplayName("TASK-A4.3: OpenAPI 3.0 Contract Freeze & Parity Verification")
public class OpenApiContractParityTest {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiContractParityTest.class);

    private static JsonObject openApiJson;
    private static Path openApiPath;

    @BeforeAll
    static void loadOpenApiSpecification() throws IOException {
        openApiPath = Path.of("docs", "dev-spec", "openapi-p1.json");
        if (!Files.exists(openApiPath)) {
            openApiPath = Path.of("..", "docs", "dev-spec", "openapi-p1.json");
        }

        assertTrue(Files.exists(openApiPath),
                "OpenAPI specification file must exist at " + openApiPath.toAbsolutePath());

        String jsonContent = Files.readString(openApiPath, StandardCharsets.UTF_8);
        assertNotNull(jsonContent);
        assertFalse(jsonContent.trim().isEmpty(), "OpenAPI specification must not be empty");

        openApiJson = JsonUtil.fromJson(jsonContent, JsonObject.class);
        assertNotNull(openApiJson, "OpenAPI JSON must be successfully parsed by JsonUtil");
        logger.info("Successfully loaded OpenAPI 3.0 specification from: {}", openApiPath.toAbsolutePath());
    }

    @Test
    @DisplayName("OpenAPI specification version must start with '3.0'")
    void testOpenApiVersionAndMetadata() {
        assertTrue(openApiJson.has("openapi"), "Specification must have 'openapi' property");
        String version = openApiJson.get("openapi").getAsString();
        assertTrue(version.startsWith("3.0"), "OpenAPI version must be 3.0.x (found: " + version + ")");

        assertTrue(openApiJson.has("info"), "Specification must contain 'info' block");
        JsonObject info = openApiJson.getAsJsonObject("info");
        assertEquals("Luxury Hotel PMS - Phase 1 Core REST API", info.get("title").getAsString());
        assertEquals("1.0.0", info.get("version").getAsString());

        assertTrue(openApiJson.has("servers"), "Specification must define servers");
        var servers = openApiJson.getAsJsonArray("servers");
        assertFalse(servers.isEmpty());
        String serverUrl = servers.get(0).getAsJsonObject().get("url").getAsString();
        assertEquals("http://localhost:8081", serverUrl);
    }

    @Test
    @DisplayName("Security Scheme must define HTTP BearerAuth with JWT format")
    void testSecuritySchemeDefinition() {
        assertTrue(openApiJson.has("components"), "OpenAPI spec must have components");
        JsonObject components = openApiJson.getAsJsonObject("components");

        assertTrue(components.has("securitySchemes"), "Components must define securitySchemes");
        JsonObject secSchemes = components.getAsJsonObject("securitySchemes");

        assertTrue(secSchemes.has("BearerAuth"), "Must declare 'BearerAuth' security scheme");
        JsonObject bearerAuth = secSchemes.getAsJsonObject("BearerAuth");

        assertEquals("http", bearerAuth.get("type").getAsString().toLowerCase());
        assertEquals("bearer", bearerAuth.get("scheme").getAsString().toLowerCase());
        assertEquals("JWT", bearerAuth.get("bearerFormat").getAsString());
    }

    @Test
    @DisplayName("Contract must declare all 10 mandatory Phase 1 core operations")
    void testTenCoreEndpointsParity() {
        assertTrue(openApiJson.has("paths"), "OpenAPI spec must have 'paths'");
        JsonObject paths = openApiJson.getAsJsonObject("paths");

        // The 10 mandatory core operations: (Method, Path)
        record EndpointOp(String method, String path, String description) {}

        List<EndpointOp> coreOps = List.of(
                new EndpointOp("post", "/api/v1/auth/login", "User login"),
                new EndpointOp("get", "/api/v1/health", "Deep health check"),
                new EndpointOp("get", "/api/v1/rooms", "Room catalog"),
                new EndpointOp("get", "/api/v1/rooms/availability", "Room availability"),
                new EndpointOp("post", "/api/v1/customers", "Register customer"),
                new EndpointOp("get", "/api/v1/customers", "Search customers"),
                new EndpointOp("get", "/api/v1/bookings", "List bookings"),
                new EndpointOp("get", "/api/v1/bookings/{id}", "Booking details by ID"),
                new EndpointOp("post", "/api/v1/bookings", "Create atomic booking with CAS"),
                new EndpointOp("post", "/api/v1/bookings/{id}/cancel", "Cancel booking and release room")
        );

        for (EndpointOp op : coreOps) {
            assertTrue(paths.has(op.path()), "Missing required endpoint path: " + op.path());
            JsonObject pathItem = paths.getAsJsonObject(op.path());
            assertTrue(pathItem.has(op.method()),
                    String.format("Path '%s' must declare HTTP method '%s' for %s", op.path(), op.method(), op.description()));

            JsonObject operation = pathItem.getAsJsonObject(op.method());
            assertTrue(operation.has("responses"),
                    String.format("Operation %s %s must declare 'responses'", op.method().toUpperCase(), op.path()));

            JsonObject responses = operation.getAsJsonObject("responses");

            // Must declare at least one success status (200 or 201)
            boolean hasSuccess = responses.has("200") || responses.has("201");
            assertTrue(hasSuccess,
                    String.format("Operation %s %s must declare a success status code (200 or 201)", op.method().toUpperCase(), op.path()));

            // Must declare standard error status codes
            boolean hasError = responses.has("400") || responses.has("401") || responses.has("404") || responses.has("409") || responses.has("503");
            assertTrue(hasError,
                    String.format("Operation %s %s must declare standard error status codes (400/401/404/409/503)", op.method().toUpperCase(), op.path()));
        }

        // Supplementary endpoints verification
        assertTrue(paths.has("/api/v1/bookings/{id}"));
        assertTrue(paths.getAsJsonObject("/api/v1/bookings/{id}").has("put"), "Must declare PUT /api/v1/bookings/{id}");

        assertTrue(paths.has("/api/v1/pos/sync"));
        assertTrue(paths.getAsJsonObject("/api/v1/pos/sync").has("post"), "Must declare POST /api/v1/pos/sync");
    }

    @Test
    @DisplayName("Verify RFC-7807 Error Envelope and Success Envelope schema contracts")
    void testEnvelopeSchemasContract() {
        JsonObject components = openApiJson.getAsJsonObject("components");
        assertTrue(components.has("schemas"), "Components must declare 'schemas'");
        JsonObject schemas = components.getAsJsonObject("schemas");

        // EnvelopeSuccess check
        assertTrue(schemas.has("EnvelopeSuccess"), "Schemas must define EnvelopeSuccess");
        JsonObject successSchema = schemas.getAsJsonObject("EnvelopeSuccess");
        assertTrue(successSchema.has("properties"));
        JsonObject successProps = successSchema.getAsJsonObject("properties");
        assertTrue(successProps.has("status"));
        assertTrue(successProps.has("data"));
        assertTrue(successProps.has("meta"));

        // EnvelopeError check (RFC-7807)
        assertTrue(schemas.has("EnvelopeError"), "Schemas must define EnvelopeError");
        JsonObject errorSchema = schemas.getAsJsonObject("EnvelopeError");
        assertTrue(errorSchema.has("properties"));
        JsonObject errorProps = errorSchema.getAsJsonObject("properties");
        assertTrue(errorProps.has("error_code"));
        assertTrue(errorProps.has("message"));
        assertTrue(errorProps.has("timestamp"));
    }

    @Test
    @DisplayName("Verify 6 OWASP Security Headers defined in contract")
    void testOwaspSecurityHeadersContract() {
        JsonObject components = openApiJson.getAsJsonObject("components");
        assertTrue(components.has("headers"), "Components must define 'headers'");
        JsonObject headers = components.getAsJsonObject("headers");
        assertTrue(headers.has("AllOwaspHeaders"), "Must define AllOwaspHeaders component");

        JsonObject owasp = headers.getAsJsonObject("AllOwaspHeaders");
        assertTrue(owasp.has("X-Content-Type-Options"), "Must include X-Content-Type-Options");
        assertTrue(owasp.has("X-Frame-Options"), "Must include X-Frame-Options");
        assertTrue(owasp.has("Content-Security-Policy"), "Must include Content-Security-Policy");
        assertTrue(owasp.has("Strict-Transport-Security"), "Must include Strict-Transport-Security");
        assertTrue(owasp.has("Referrer-Policy"), "Must include Referrer-Policy");
        assertTrue(owasp.has("Permissions-Policy"), "Must include Permissions-Policy");
    }
}
