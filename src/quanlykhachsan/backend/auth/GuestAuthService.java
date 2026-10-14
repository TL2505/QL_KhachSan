package quanlykhachsan.backend.auth;

import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import io.jsonwebtoken.Jwts;
import quanlykhachsan.backend.customer.GuestIngestionService;
import quanlykhachsan.backend.utils.*;

/** OTPs are single-use and never returned by public APIs or logged. */
public final class GuestAuthService {
    @FunctionalInterface public interface Delivery { void send(String phone, String email, String otp) throws Exception; }
    private final Delivery delivery;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    public GuestAuthService() {
        this((phone,email,otp) -> {
            if (!"mock".equals(System.getenv("HOTEL_OTP_MODE"))) throw new IllegalStateException("OTP delivery is not configured");
            // Approved development sink. Codes can be observed only by injected test fixtures.
        });
    }
    public GuestAuthService(Delivery delivery) { this.delivery = java.util.Objects.requireNonNull(delivery); }
    private Attempts attempts(String phone) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().created > 600000);
        if (attempts.size() >= 10000 && !attempts.containsKey(phone)) throw new IllegalStateException("OTP service is busy");
        return attempts.computeIfAbsent(phone, key -> new Attempts(now));
    }
    public void request(String rawPhone, String email) throws Exception {
        String phone = PortalValidation.phone(rawPhone); PortalValidation.email(email);
        Attempts state = attempts(phone);
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (now - state.requested < 60000) throw new RateLimitException();
            String otp = String.format(java.util.Locale.ROOT,"%06d",random.nextInt(1000000));
            try (Connection c = DatabaseConnection.getConnection()) {
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement q = c.prepareStatement("UPDATE guest_otps SET consumed_at=CURRENT_TIMESTAMP WHERE phone=? AND consumed_at IS NULL")) { q.setString(1,phone); q.executeUpdate(); }
                    try (PreparedStatement q = c.prepareStatement("INSERT INTO guest_otps(phone,email,otp_code,expires_at) VALUES (?,?,?,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 5 MINUTE))")) {
                        q.setString(1,phone); q.setString(2,email); q.setString(3,otp); q.executeUpdate();
                    }
                    delivery.send(phone,email,otp); c.commit(); state.requested = now; state.failed = 0;
                } catch (Exception ex) { c.rollback(); throw ex; }
                finally { c.setAutoCommit(true); }
            }
        }
    }
    public Verified verify(String rawPhone, String code) throws SQLException {
        String phone = PortalValidation.phone(rawPhone);
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("Invalid OTP");
        Attempts state = attempts(phone);
        synchronized (state) {
            if (state.failed >= 5) throw new RateLimitException();
            try (Connection c = DatabaseConnection.getConnection()) {
                GuestIngestionService.lock(c,phone,true);
                c.setAutoCommit(false);
                try {
                    int updated;
                    try (PreparedStatement q = c.prepareStatement("UPDATE guest_otps SET consumed_at=CURRENT_TIMESTAMP WHERE phone=? AND otp_code=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP")) {
                        q.setString(1,phone); q.setString(2,code); updated=q.executeUpdate();
                    }
                    if (updated != 1) { state.failed++; throw new IllegalArgumentException("Invalid or expired OTP"); }
                    int customerId = new GuestIngestionService().ingest(c,null,phone,null);
                    Instant now = Instant.now();
                    String token = Jwts.builder().subject(Integer.toString(customerId)).claim("role","guest")
                        .claim("phone",phone).claim("customer_id",customerId).issuedAt(Date.from(now))
                        .expiration(Date.from(now.plusSeconds(86400))).signWith(JwtTokenUtil.signingKey()).compact();
                    c.commit(); return new Verified(token, customerId,phone);
                } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
                finally { c.setAutoCommit(true); GuestIngestionService.lock(c,phone,false); }
            }
        }
    }
    public record Verified(String token,int customerId,String phone) { }
    public static final class RateLimitException extends IllegalStateException { private static final long serialVersionUID=1L; }
    private static final class Attempts {
        final long created; long requested; int failed;
        Attempts(long now) { created=now; }
    }
}
