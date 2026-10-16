package quanlykhachsan.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/** Process-local revocation until signed expiry. Store fingerprints, never bearer tokens. */
public final class TokenBlacklistService {
    private static final TokenBlacklistService INSTANCE = new TokenBlacklistService(Clock.systemUTC(), 100_000);
    private final Clock clock;
    private final int capacity;
    private final Map<String, Instant> revoked = new HashMap<>();

    public TokenBlacklistService(Clock clock, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.clock = java.util.Objects.requireNonNull(clock);
        this.capacity = capacity;
    }

    public static TokenBlacklistService shared() { return INSTANCE; }

    public synchronized boolean revoke(String token, Instant expiresAt) {
        Instant now = clock.instant();
        revoked.values().removeIf(expiry -> !expiry.isAfter(now));
        if (!expiresAt.isAfter(now)) return true;
        String key = fingerprint(token);
        if (!revoked.containsKey(key) && revoked.size() >= capacity) return false;
        revoked.put(key, expiresAt);
        return true;
    }

    public synchronized boolean isRevoked(String token) {
        String key = fingerprint(token);
        Instant expiry = revoked.get(key);
        if (expiry == null) return false;
        if (!expiry.isAfter(clock.instant())) { revoked.remove(key); return false; }
        return true;
    }

    private static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
