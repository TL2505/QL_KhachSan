package quanlykhachsan.backend.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import quanlykhachsan.backend.auth.dto.AuthUserInfo;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

/** Google Identity verification followed by a short-lived email OTP challenge. */
public final class GoogleOtpAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESENDS = 2;
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(15);
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Clock clock;
    private final IdentityVerifier identityVerifier;
    private final OtpSender otpSender;
    private final LoginCompleter loginCompleter;

    @FunctionalInterface interface IdentityVerifier { GoogleIdentity verify(String credential) throws AuthFailure; }
    @FunctionalInterface interface OtpSender { void send(String email, String otp) throws Exception; }
    @FunctionalInterface interface LoginCompleter { AuthUserInfo complete(GoogleIdentity identity) throws AuthFailure; }
    public GoogleOtpAuthService() {
        clock = Clock.systemUTC(); identityVerifier = this::verifyGoogleToken; otpSender = this::sendOtp;
        loginCompleter = this::completeLogin;
    }
    GoogleOtpAuthService(Clock clock, IdentityVerifier verifier, OtpSender sender, LoginCompleter completer) {
        this.clock = clock; identityVerifier = verifier; otpSender = sender; loginCompleter = completer;
    }

    public record StartResult(String challengeId, String maskedEmail, Instant expiresAt) { }
    record GoogleIdentity(String subject, String email, String name) { }
    private static final class Challenge {
        final GoogleIdentity identity;
        final byte[] salt;
        byte[] otpHash;
        Instant expiresAt;
        Instant sessionExpiresAt;
        int attempts;
        int resends;
        boolean used;
        Challenge(GoogleIdentity identity, String otp) {
            this.identity = identity;
            this.salt = randomBytes(16);
            this.otpHash = hash(salt, otp);
        }
    }

    public boolean isConfigured() {
        return !value("GOOGLE_CLIENT_ID").isBlank() && smtpConfigured();
    }
    public String clientId() { return value("GOOGLE_CLIENT_ID"); }
    public boolean isMailConfigured() { return smtpConfigured(); }
    public void sendAccountOtp(String recipient, String otp, String purpose) throws Exception {
        if (!smtpConfigured()) throw new IllegalStateException("Email delivery is not configured");
        sendOtp(recipient, otp, purpose);
    }

    public StartResult begin(String credential) throws AuthFailure {
        challenges.entrySet().removeIf(entry -> !clock.instant().isBefore(entry.getValue().sessionExpiresAt));
        GoogleIdentity identity = identityVerifier.verify(credential);
        String otp = nextOtp();
        Challenge challenge = new Challenge(identity, otp);
        String id = randomId();
        try {
            otpSender.send(identity.email(), otp);
        } catch (Exception e) {
            throw new AuthFailure(503, "Chưa thể gửi mã xác thực. Vui lòng thử lại sau.");
        }
        // Start the validity window after SMTP delivery, not before a slow send.
        challenge.expiresAt = clock.instant().plus(OTP_TTL);
        challenge.sessionExpiresAt = clock.instant().plus(CHALLENGE_TTL);
        challenges.put(id, challenge);
        return new StartResult(id, maskEmail(identity.email()), challenge.expiresAt);
    }

    public StartResult resend(String challengeId) throws AuthFailure {
        Challenge c = challenge(challengeId);
        synchronized (c) {
            requireActive(challengeId, c);
            if (c.resends >= MAX_RESENDS) throw new AuthFailure(429, "Bạn đã dùng hết số lần gửi lại mã. Vui lòng đăng nhập lại bằng Google.");
            String otp = nextOtp();
            try {
                otpSender.send(c.identity.email(), otp);
            } catch (Exception e) {
                throw new AuthFailure(503, "Chưa thể gửi lại mã xác thực. Vui lòng thử lại sau.");
            }
            c.resends++;
            c.otpHash = hash(c.salt, otp);
            c.expiresAt = clock.instant().plus(OTP_TTL);
            c.sessionExpiresAt = clock.instant().plus(CHALLENGE_TTL);
            return new StartResult(challengeId, maskEmail(c.identity.email()), c.expiresAt);
        }
    }

    public AuthUserInfo verify(String challengeId, String otp) throws AuthFailure {
        Challenge c = challenge(challengeId);
        synchronized (c) {
            requireActive(challengeId, c);
            if (!clock.instant().isBefore(c.expiresAt)) throw new AuthFailure(422, "Mã xác thực đã hết hạn. Bấm Gửi lại mã để nhận mã mới.");
            if (otp == null || !otp.matches("\\d{4}")) throw new AuthFailure(400, "Mã xác thực phải gồm đúng 4 chữ số.");
            if (!MessageDigest.isEqual(c.otpHash, hash(c.salt, otp))) {
                c.attempts++;
                if (c.attempts >= MAX_ATTEMPTS) { challenges.remove(challengeId); throw new AuthFailure(429, "Bạn đã nhập sai quá nhiều lần. Vui lòng đăng nhập lại bằng Google."); }
                throw new AuthFailure(401, "Mã xác thực không đúng. Vui lòng kiểm tra lại.");
            }
            // A database failure must not consume a correct OTP and then look expired.
            AuthUserInfo info = loginCompleter.complete(c.identity);
            c.used = true;
            challenges.remove(challengeId, c);
            return info;
        }
    }

    private AuthUserInfo completeLogin(GoogleIdentity identity) throws AuthFailure {
        User user = findOrCreateCustomer(identity);
        AuthUserInfo info = AuthMapper.toUserInfo(user, "CUSTOMER");
        info.setToken(JwtTokenUtil.issueToken(user.getId(), user.getUsername(), "CUSTOMER"));
        return info;
    }

    private Challenge challenge(String id) throws AuthFailure {
        if (id == null || id.isBlank()) throw new AuthFailure(400, "Phiên xác thực không hợp lệ. Vui lòng đăng nhập lại bằng Google.");
        Challenge c = challenges.get(id);
        if (c == null) throw new AuthFailure(410, "Phiên xác thực không còn tồn tại. Vui lòng chọn lại Google để nhận mã mới.");
        requireActive(id, c);
        return c;
    }

    private void requireActive(String id, Challenge c) throws AuthFailure {
        if (c.used || !clock.instant().isBefore(c.sessionExpiresAt)) {
            challenges.remove(id, c);
            throw new AuthFailure(410, "Phiên xác thực đã kết thúc. Vui lòng chọn lại Google để nhận mã mới.");
        }
    }

    private GoogleIdentity verifyGoogleToken(String credential) throws AuthFailure {
        requireConfigured();
        if (credential == null || credential.isBlank() || credential.length() > 12_000) throw new AuthFailure(400, "Google không trả về thông tin xác thực hợp lệ.");
        try {
            String endpoint = "https://oauth2.googleapis.com/tokeninfo?id_token=" + URLEncoder.encode(credential, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(12)).GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new AuthFailure(401, "Không thể xác thực tài khoản Google. Vui lòng chọn lại tài khoản.");
            JsonObject token = JsonParser.parseString(response.body()).getAsJsonObject();
            String audience = string(token, "aud");
            String issuer = string(token, "iss");
            String subject = string(token, "sub");
            String email = string(token, "email").toLowerCase(Locale.ROOT);
            boolean verified = "true".equalsIgnoreCase(string(token, "email_verified"));
            if (!clientId().equals(audience) || !("accounts.google.com".equals(issuer) || "https://accounts.google.com".equals(issuer))
                    || subject.isBlank() || email.isBlank() || !verified) {
                throw new AuthFailure(401, "Thông tin tài khoản Google không hợp lệ hoặc email chưa được xác minh.");
            }
            return new GoogleIdentity(subject, email, string(token, "name"));
        } catch (AuthFailure e) { throw e;
        } catch (Exception e) { throw new AuthFailure(503, "Chưa thể xác thực với Google. Vui lòng thử lại sau."); }
    }

    private User findOrCreateCustomer(GoogleIdentity identity) throws AuthFailure {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                User existing = null;
                try (PreparedStatement q = c.prepareStatement("SELECT * FROM users WHERE google_subject=?")) {
                    q.setString(1, identity.subject());
                    try (ResultSet r = q.executeQuery()) { if (r.next()) existing = user(r); }
                }
                if (existing != null) {
                    if (!"active".equalsIgnoreCase(existing.getStatus()) || existing.getCustomerId() == null) throw new AuthFailure(403, "Tài khoản Google này không có quyền truy cập cổng khách hàng.");
                    c.commit(); return existing;
                }
                try (PreparedStatement q = c.prepareStatement("SELECT id FROM users WHERE LOWER(email)=LOWER(?) AND google_subject IS NULL LIMIT 1")) {
                    q.setString(1, identity.email());
                    try (ResultSet r = q.executeQuery()) { if (r.next()) throw new AuthFailure(409, "Email này đã thuộc một tài khoản Aurelia. Vui lòng đăng nhập bằng mật khẩu để liên kết an toàn tại Hồ sơ & bảo mật."); }
                }
                int roleId;
                try (PreparedStatement q = c.prepareStatement("SELECT id FROM roles WHERE LOWER(name)='customer' LIMIT 1"); ResultSet r = q.executeQuery()) {
                    if (!r.next()) throw new AuthFailure(500, "Không tìm thấy quyền khách hàng trong hệ thống."); roleId = r.getInt(1);
                }
                int customerId;
                try (PreparedStatement q = c.prepareStatement("INSERT INTO customers(full_name,identity_card,phone,email,address) VALUES(?,NULL,'',?,NULL)", Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, identity.name().isBlank() ? identity.email() : identity.name()); q.setString(2, identity.email()); q.executeUpdate();
                    try (ResultSet keys = q.getGeneratedKeys()) { if (!keys.next()) throw new AuthFailure(500, "Không thể tạo hồ sơ khách hàng."); customerId = keys.getInt(1); }
                }
                String username = "google_" + identity.subject();
                try (PreparedStatement q = c.prepareStatement("INSERT INTO users(username,password,role_id,status,full_name,email,phone,customer_id,google_subject) VALUES(?,?,?,?,?,?,NULL,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, username); q.setString(2, SecurityUtil.hashPassword(randomId())); q.setInt(3, roleId); q.setString(4, "active"); q.setString(5, identity.name().isBlank() ? identity.email() : identity.name()); q.setString(6, identity.email()); q.setInt(7, customerId); q.setString(8, identity.subject()); q.executeUpdate();
                    try (ResultSet keys = q.getGeneratedKeys()) { if (!keys.next()) throw new AuthFailure(500, "Không thể tạo tài khoản Google."); int id = keys.getInt(1); c.commit(); return new User(id, username, "", roleId, "active", identity.name().isBlank() ? identity.email() : identity.name(), identity.email(), null, customerId); }
                }
            } catch (AuthFailure e) { c.rollback(); throw e; } catch (Exception e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (AuthFailure e) { throw e; } catch (Exception e) { throw new AuthFailure(500, "Chưa thể hoàn tất đăng nhập Google."); }
    }

    private static User user(ResultSet r) throws Exception { return new User(r.getInt("id"), r.getString("username"), r.getString("password"), r.getInt("role_id"), r.getString("status"), r.getString("full_name"), r.getString("email"), r.getString("phone"), (Integer) r.getObject("customer_id")); }
    private void requireConfigured() throws AuthFailure { if (!isConfigured()) throw new AuthFailure(503, "Đăng nhập Google chưa được cấu hình trên máy chủ."); }
    private boolean smtpConfigured() { return !value("SMTP_HOST").isBlank() && !value("SMTP_USERNAME").isBlank() && !value("SMTP_APP_PASSWORD").isBlank() && !value("SMTP_FROM").isBlank(); }
    private static String value(String key) { String v = System.getenv(key); return v == null ? "" : v.trim(); }
    private static String string(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : ""; }
    private static byte[] randomBytes(int count) { byte[] bytes = new byte[count]; RANDOM.nextBytes(bytes); return bytes; }
    private static String randomId() { return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32)); }
    private static String nextOtp() { return String.format(Locale.ROOT, "%04d", RANDOM.nextInt(10_000)); }
    private static byte[] hash(byte[] salt, String otp) { try { MessageDigest d = MessageDigest.getInstance("SHA-256"); d.update(salt); return d.digest(otp.getBytes(StandardCharsets.US_ASCII)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String maskEmail(String email) { int at = email.indexOf('@'); if (at <= 1) return "***" + email.substring(Math.max(at, 0)); return email.charAt(0) + "***" + email.substring(at); }

    private void sendOtp(String recipient, String otp) throws Exception {
        sendOtp(recipient, otp, "đăng nhập");
    }
    private void sendOtp(String recipient, String otp, String purpose) throws Exception {
        String host = value("SMTP_HOST"); int port = Integer.parseInt(value("SMTP_PORT").isBlank() ? "465" : value("SMTP_PORT"));
        String username = value("SMTP_USERNAME"), password = value("SMTP_APP_PASSWORD"), from = value("SMTP_FROM");
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(15000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            expect(in, 220); command(out, in, "EHLO aurelia.local", 250); command(out, in, "AUTH LOGIN", 334);
            command(out, in, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)), 334);
            command(out, in, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            command(out, in, "MAIL FROM:<" + from + ">", 250); command(out, in, "RCPT TO:<" + recipient + ">", 250); command(out, in, "DATA", 354);
            out.write(otpMessage(from, recipient, otp, purpose) + "\r\n.\r\n"); out.flush(); expect(in, 250); command(out, in, "QUIT", 221);
        }
    }
    // Keep the SMTP wire payload ASCII while preserving Vietnamese as UTF-8 MIME.
    static String otpMessage(String from, String recipient, String otp) {
        return otpMessage(from, recipient, otp, "đăng nhập");
    }
    static String otpMessage(String from, String recipient, String otp, String purpose) {
        String subject = Base64.getEncoder().encodeToString("Mã xác thực Aurelia Grand Palace".getBytes(StandardCharsets.UTF_8));
        String body = "Mã OTP " + purpose + " Aurelia của bạn là: " + otp + "\r\n\r\nMã có hiệu lực trong 5 phút và chỉ được dùng một lần. Không chia sẻ mã này. Nếu bạn không yêu cầu " + purpose + ", hãy bỏ qua email này.";
        String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return "From: Aurelia Grand Palace <" + from + ">\r\nTo: <" + recipient + ">\r\nSubject: =?UTF-8?B?" + subject
                + "?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + encodedBody;
    }
    private static void command(BufferedWriter out, BufferedReader in, String line, int expected) throws Exception { out.write(line + "\r\n"); out.flush(); expect(in, expected); }
    private static void expect(BufferedReader in, int expected) throws Exception { String line, last = null; do { line = in.readLine(); if (line == null) throw new IllegalStateException("SMTP closed connection"); last = line; } while (line.length() > 3 && line.charAt(3) == '-'); if (last == null || !last.startsWith(Integer.toString(expected))) throw new IllegalStateException("SMTP rejected request"); }

    public static final class AuthFailure extends Exception { public final int status; public AuthFailure(int status, String message) { super(message); this.status = status; } }
}
