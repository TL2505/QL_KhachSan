package quanlykhachsan.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.auth.OAuthController;

import static org.junit.jupiter.api.Assertions.*;

public class OAuthControllerTest {

    private final OAuthController controller = new OAuthController();

    @AfterEach
    void tearDown() {
        System.clearProperty("hotel.oauth.allow_mock");
    }

    @Test
    @DisplayName("OAuth Security: Reject unverified mock tokens by default (CWE-347 mitigation)")
    void testMockTokenRejectionByDefault() {
        System.clearProperty("hotel.oauth.allow_mock");
        if (!OAuthController.isMockAuthAllowed()) {
            assertThrows(SecurityException.class, () -> 
                controller.verifyGoogleToken("mock_google_id_token_test_12345")
            );
            assertThrows(SecurityException.class, () -> 
                controller.verifyFacebookToken("mock_fb_access_token_test_67890")
            );
        }
    }

    @Test
    @DisplayName("OAuth Guarded Sandbox: verifyGoogleToken succeeds when mock is explicitly enabled")
    void testGoogleMockTokenVerificationInSandbox() {
        System.setProperty("hotel.oauth.allow_mock", "true");
        OAuthController.SocialProfile profile = controller.verifyGoogleToken("mock_google_id_token_test_12345");
        assertNotNull(profile);
        assertEquals("GOOGLE", profile.provider());
        assertNotNull(profile.providerId());
        assertTrue(profile.email().contains("@gmail.com"));
        assertNotNull(profile.name());
    }

    @Test
    @DisplayName("OAuth Guarded Sandbox: verifyFacebookToken succeeds when mock is explicitly enabled")
    void testFacebookMockTokenVerificationInSandbox() {
        System.setProperty("hotel.oauth.allow_mock", "true");
        OAuthController.SocialProfile profile = controller.verifyFacebookToken("mock_fb_access_token_test_67890");
        assertNotNull(profile);
        assertEquals("FACEBOOK", profile.provider());
        assertNotNull(profile.providerId());
        assertTrue(profile.email().contains("@facebook.com"));
        assertNotNull(profile.name());
    }

    @Test
    @DisplayName("OAuth: verifyGoogleToken throws IllegalArgumentException on empty token")
    void testGoogleEmptyToken() {
        assertThrows(IllegalArgumentException.class, () -> controller.verifyGoogleToken(""));
        assertThrows(IllegalArgumentException.class, () -> controller.verifyGoogleToken(null));
    }

    @Test
    @DisplayName("OAuth: verifyFacebookToken throws IllegalArgumentException on empty token")
    void testFacebookEmptyToken() {
        assertThrows(IllegalArgumentException.class, () -> controller.verifyFacebookToken(""));
        assertThrows(IllegalArgumentException.class, () -> controller.verifyFacebookToken(null));
    }
}
