package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoogleAuthRoutesTest {
    @Test void legacySocialRoutesCannotIssueSessionsFromMockOrUnsignedCredentials() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/google", new GoogleOtpAuthController());
        server.createContext("/api/v1/auth/facebook", new AuthController());
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            var base = "http://127.0.0.1:" + server.getAddress().getPort();
            for (String path : new String[]{"/api/v1/auth/google", "/api/v1/auth/facebook"}) {
                var result = client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"credential\":\"mock_google_token\",\"access_token\":\"mock_facebook_token\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(410, result.statusCode());
                assertFalse(result.body().contains("\"token\""));
                assertFalse(result.body().contains("mock_"));
            }
        } finally { server.stop(0); }
    }

    @Test void unknownOtpChallengeDoesNotIssueSession() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/google", new GoogleOtpAuthController());
        server.start();
        try {
            var result = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/auth/google/verify-otp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"challenge_id\":\"synthetic-unknown-challenge\",\"otp\":\"1234\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(410, result.statusCode());
            assertFalse(result.body().contains("\"token\""));
        } finally { server.stop(0); }
    }
}
