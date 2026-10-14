package quanlykhachsan.stubs;

import java.net.*;
import java.net.http.*;
import java.util.UUID;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MockApiServerTest {
    @Test void week5MocksExposeContractAndRejectInvalidOtpAndMissingKeys() throws Exception {
        try (var mock = new MockApiServer(0)) {
            mock.start(); var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + mock.port() + "/api/v1/";
            assertEquals(200, post(client, base, "auth/guest-otp/request", "{\"phone\":\"0901234567\"}", null).statusCode());
            var verified = post(client, base, "auth/guest-otp/verify", "{\"phone\":\"0901234567\",\"otp_code\":\"123456\"}", null);
            assertEquals(200, verified.statusCode());
            assertEquals("mock-guest-jwt", JsonParser.parseString(verified.body()).getAsJsonObject().getAsJsonObject("data").get("access_token").getAsString());
            assertEquals(400, post(client, base, "auth/guest-otp/verify", "{\"phone\":\"0901234567\",\"otp\":\"000000\"}", null).statusCode());
            assertEquals(400, post(client, base, "bookings/public", "{\"phone\":\"0901234567\"}", null).statusCode());
            assertEquals(201, post(client, base, "bookings/public", "{\"phone\":\"0901234567\"}", UUID.randomUUID().toString()).statusCode());
            assertEquals(400, post(client, base, "auth/guest-otp/request", "{", null).statusCode());
        }
    }
    private static HttpResponse<String> post(HttpClient client, String base, String path, String body, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) request.header("Idempotency-Key", key);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
