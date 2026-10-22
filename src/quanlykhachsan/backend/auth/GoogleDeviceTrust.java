package quanlykhachsan.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JwtTokenUtil;

/** A device proof is not a login token: fresh Google identity is always required. */
final class GoogleDeviceTrust implements GoogleOtpAuthService.DeviceTrust {
    static final long MAX_AGE_SECONDS = 365L * 24 * 60 * 60;
    private final Clock clock;
    private final byte[] key;
    private final Accounts accounts;
    record Account(int id, String subject, String email, String verifiedEmail, String passwordHash) { }
    @FunctionalInterface interface Accounts { Account read(String subject) throws Exception; }

    GoogleDeviceTrust() {
        this(Clock.systemUTC(), JwtTokenUtil.signingKey().getEncoded(), GoogleDeviceTrust::read);
    }
    GoogleDeviceTrust(Clock clock, byte[] signingKey, Accounts accounts) {
        this.clock = clock;
        // Domain separation prevents interchange with application JWTs.
        this.key = mac(signingKey, "aurelia-google-device-v1");
        this.accounts = accounts;
    }
    @Override public boolean recognizes(GoogleOtpAuthService.GoogleIdentity identity, String token)
            throws GoogleOtpAuthService.AuthFailure {
        if (token == null || token.length() > 512) return false;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4) return false;
        try {
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (!MessageDigest.isEqual(mac(key, payload), Base64.getUrlDecoder().decode(parts[3]))) return false;
            long expires = Long.parseLong(parts[0]);
            if (expires <= clock.instant().getEpochSecond()) return false;
            Account account = accounts.read(identity.subject());
            return eligible(account, identity)
                && MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.US_ASCII), fingerprint(account).getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException ex) { return false; }
        catch (Exception ex) { throw new GoogleOtpAuthService.AuthFailure(503, "Chưa thể kiểm tra thiết bị đăng nhập. Vui lòng thử lại."); }
    }
    @Override public String remember(GoogleOtpAuthService.GoogleIdentity identity)
            throws GoogleOtpAuthService.AuthFailure {
        try {
            Account account = accounts.read(identity.subject());
            if (!eligible(account, identity)) return null;
            byte[] nonce = new byte[24]; new SecureRandom().nextBytes(nonce);
            String payload = (clock.instant().getEpochSecond() + MAX_AGE_SECONDS) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "." + fingerprint(account);
            return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key, payload));
        } catch (Exception ex) { throw new GoogleOtpAuthService.AuthFailure(503, "Chưa thể ghi nhận thiết bị đăng nhập. Vui lòng thử lại."); }
    }
    private static boolean eligible(Account account, GoogleOtpAuthService.GoogleIdentity identity) {
        return account != null && identity.subject().equals(account.subject())
            && identity.email().equalsIgnoreCase(account.email())
            && account.email().equalsIgnoreCase(account.verifiedEmail());
    }
    private static String fingerprint(Account account) throws Exception {
        String state = account.id() + "|" + account.subject() + "|" + account.email().toLowerCase(java.util.Locale.ROOT)
            + "|" + account.verifiedEmail().toLowerCase(java.util.Locale.ROOT) + "|" + account.passwordHash();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
    }
    private static byte[] mac(byte[] secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) { throw new IllegalStateException("Device signing unavailable", ex); }
    }
    private static Account read(String subject) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT id,google_subject,email,verified_email,password FROM users WHERE google_subject=? AND status='active' AND customer_id IS NOT NULL")) {
            q.setString(1, subject);
            try (ResultSet r = q.executeQuery()) {
                return r.next() ? new Account(r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)) : null;
            }
        }
    }
}
