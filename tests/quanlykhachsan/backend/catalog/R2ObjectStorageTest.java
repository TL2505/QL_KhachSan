package quanlykhachsan.backend.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class R2ObjectStorageTest {
    @Test
    void signsS3RequestWithCloudflareR2ScopeAndStableSigV4() {
        URI uri = URI.create("https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com"
                + "/hotel-media/12345678-1234-1234-1234-123456789abc.jpg");
        String payloadHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        String authorization = R2ObjectStorage.authorizationHeader("PUT", uri, "image/jpeg", payloadHash,
                "AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", Instant.parse("2026-10-26T05:00:00Z"));

        assertEquals("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261026/auto/s3/aws4_request"
                + ", SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date"
                + ", Signature=2186ebfc0c5a9714ca01ce2dea41d405fe134e900decf39e02b993182034d753",
                authorization);
    }

    @Test
    void buildsProviderMarkedObjectKeysAndKeepsLocalKeysDistinct() {
        String id = "12345678-1234-1234-1234-123456789abc";
        String storageKey = R2ObjectStorage.storageKey(id, ".png");

        assertEquals("r2:" + id + ".png", storageKey);
        assertTrue(R2ObjectStorage.isR2StorageKey(storageKey));
        assertEquals(id + ".png", R2ObjectStorage.objectKeyFromStorageKey(storageKey));
        assertFalse(R2ObjectStorage.isR2StorageKey(id));
        assertThrows(IllegalArgumentException.class, () -> R2ObjectStorage.objectKeyFromStorageKey("r2:../secret"));
    }

    @Test
    void usesApiProxyByDefaultAndOptionalHttpsPublicBaseWhenConfigured() {
        var proxyStorage = R2ObjectStorage.forTest("account12345", "hotel-images", "key", "secret", "", java.time.Clock.systemUTC());
        var publicStorage = R2ObjectStorage.forTest("account12345", "hotel-images", "key", "secret",
                "https://images.example.test/rooms/", java.time.Clock.systemUTC());

        assertEquals("/api/v1/media/12345678-1234-1234-1234-123456789abc",
                proxyStorage.publicUrl("12345678-1234-1234-1234-123456789abc", "12345678-1234-1234-1234-123456789abc.jpg"));
        assertEquals("https://images.example.test/rooms/12345678-1234-1234-1234-123456789abc.jpg",
                publicStorage.publicUrl("12345678-1234-1234-1234-123456789abc", "12345678-1234-1234-1234-123456789abc.jpg"));
    }

    @Test
    void refusesInsecureOrCredentialBearingPublicBaseUrls() {
        assertThrows(IllegalArgumentException.class, () -> R2ObjectStorage.normalizePublicBaseUrl("http://images.example.test"));
        assertThrows(IllegalArgumentException.class, () -> R2ObjectStorage.normalizePublicBaseUrl("https://user:pass@images.example.test"));
        assertThrows(IllegalArgumentException.class, () -> R2ObjectStorage.normalizePublicBaseUrl("https://images.example.test/?token=secret"));
    }
}
