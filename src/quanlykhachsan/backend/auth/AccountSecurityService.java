package quanlykhachsan.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import quanlykhachsan.backend.utils.SecurityUtil;

/** User-bound, purpose-bound email OTPs. No plaintext codes or passwords are retained. */
public final class AccountSecurityService {
    public record Account(int id, String username, String fullName, String phone, String email, String verifiedEmail, String passwordHash) {
        public boolean emailVerified() { return !clean(email).isEmpty() && clean(email).equals(clean(verifiedEmail)); }
    }
    public interface Repository {
        Account read(int userId) throws Exception;
        Account profile(int userId, String name, String phone, String email) throws Exception;
        void verifyEmail(int userId, String expectedEmail) throws Exception;
        void password(int userId, String expectedHash, String expectedEmail, String newHash) throws Exception;
    }
    @FunctionalInterface public interface Mailer { void send(String email, String code, String purpose) throws Exception; }
    public static final class Failure extends Exception {
        public final int status, retryAfter;
        public Failure(int status, String message) { this(status, message, 0); }
        public Failure(int status, String message, int retryAfter) { super(message); this.status=status; this.retryAfter=retryAfter; }
    }
    public record Start(String challengeId, String maskedEmail, Instant expiresAt, Instant resendAt) { }
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS=5, MAX_SENDS=10;
    private final Clock clock;
    private final Repository repository;
    private final Mailer mailer;
    private final BooleanSupplier configured;
    private final Map<String, Gate> gates = new ConcurrentHashMap<>();
    private static final class Gate {
        Instant hour, nextSend=Instant.EPOCH, lockedUntil=Instant.EPOCH;
        int sends, failures;
        Challenge challenge;
        Gate(Instant now) { hour=now; }
    }
    private record Challenge(String id, String email, String passwordHash, byte[] salt, byte[] hash, Instant expiresAt) { }
    public AccountSecurityService() {
        var email = new GoogleOtpAuthService();
        clock=Clock.systemUTC(); repository=new AccountSecurityRepository(); mailer=email::sendAccountOtp; configured=email::isMailConfigured;
    }
    public AccountSecurityService(Clock clock, Repository repository, Mailer mailer) {
        this.clock=clock; this.repository=repository; this.mailer=mailer; configured=()->true;
    }
    public Account account(int userId) throws Exception {
        Account account=repository.read(userId);
        if (account==null) throw new Failure(403,"Tài khoản không còn hoạt động.");
        return account;
    }
    public Map<String,Object> profile(int userId) throws Exception { return publicProfile(account(userId)); }
    public Map<String,Object> saveProfile(int userId, String name, String phone, String email) throws Exception {
        account(userId);
        if (name==null || name.isBlank() || name.length()>100 || name.chars().anyMatch(Character::isISOControl)) throw new Failure(400,"Họ tên phải có từ 1 đến 100 ký tự.");
        phone=phone==null?"":phone.trim(); email=clean(email);
        if (!phone.isEmpty() && !phone.matches("[+0-9 ()-]{7,20}")) throw new Failure(400,"Số điện thoại không hợp lệ.");
        if (!email.isEmpty() && (email.length()>100 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.chars().anyMatch(Character::isISOControl))) throw new Failure(400,"Email không hợp lệ.");
        return publicProfile(repository.profile(userId,name.trim(),phone,email));
    }
    private Map<String,Object> publicProfile(Account a) {
        var result=new LinkedHashMap<String,Object>();
        result.put("userId",a.id()); result.put("username",a.username()); result.put("fullName",a.fullName());
        result.put("phone",a.phone()); result.put("email",a.email()); result.put("emailVerified",a.emailVerified()); result.put("mailConfigured",configured.getAsBoolean());
        return result;
    }
    public Start beginEmail(int userId) throws Exception { return begin(userId,"email",null); }
    public Start beginPassword(int userId, String currentPassword) throws Exception { return begin(userId,"password",currentPassword); }
    private Gate gate(int userId, String purpose) throws Failure {
        String key=userId+":"+purpose;
        if (gates.size()>=20_000 && !gates.containsKey(key)) {
            gates.entrySet().removeIf(e -> clock.instant().isAfter(e.getValue().hour.plusSeconds(7200)));
            if (gates.size()>=20_000) throw new Failure(503,"Hệ thống đang bận. Vui lòng thử lại sau.");
        }
        return gates.computeIfAbsent(key,k->new Gate(clock.instant()));
    }
    private void unlocked(Gate g) throws Failure {
        Instant now=clock.instant();
        if (now.isBefore(g.lockedUntil)) throw new Failure(429,"Đã nhập sai quá nhiều lần. Vui lòng thử lại sau.",seconds(g.lockedUntil));
        if (!now.isBefore(g.hour.plusSeconds(3600))) { g.hour=now; g.sends=0; g.failures=0; }
    }
    private int seconds(Instant until) { return (int)Math.max(1,Duration.between(clock.instant(),until).toSeconds()+1); }
    private void failed(Gate g, String message) throws Failure {
        if (++g.failures>=MAX_ATTEMPTS) { g.lockedUntil=clock.instant().plusSeconds(600); g.challenge=null; throw new Failure(429,"Đã nhập sai quá nhiều lần. Vui lòng thử lại sau 10 phút.",600); }
        throw new Failure(422,message);
    }
    private Start begin(int userId, String purpose, String currentPassword) throws Exception {
        Gate g=gate(userId,purpose);
        synchronized(g) {
            unlocked(g);
            if (clock.instant().isBefore(g.nextSend)) throw new Failure(429,"Vui lòng chờ trước khi gửi lại mã.",seconds(g.nextSend));
            if (g.sends>=MAX_SENDS) throw new Failure(429,"Đã đạt giới hạn gửi mã trong một giờ.",seconds(g.hour.plusSeconds(3600)));
            Account a=account(userId);
            if (purpose.equals("email") && a.emailVerified()) throw new Failure(409,"Email này đã được xác minh. Không cần gửi lại mã.");
            String email=clean(a.email());
            if (email.isEmpty() || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new Failure(400,"Hãy lưu một email hợp lệ trong hồ sơ trước.");
            if (purpose.equals("password")) {
                if (!a.emailVerified()) throw new Failure(409,"Hãy xác minh email trước khi đổi mật khẩu.");
                if (currentPassword==null || !SecurityUtil.verifyPassword(currentPassword,a.passwordHash())) failed(g,"Mật khẩu hiện tại không chính xác.");
            }
            if (!configured.getAsBoolean()) throw new Failure(503,"Chưa thể gửi email xác thực. Vui lòng liên hệ quản trị viên.");
            String otp=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
            byte[] salt=random(16);
            g.nextSend=clock.instant().plusSeconds(60); g.sends++;
            try { mailer.send(email,otp,purpose.equals("email")?"xác minh email":"đổi mật khẩu"); }
            catch (Exception error) { throw new Failure(503,"Chưa thể gửi mã xác thực. Vui lòng thử lại sau.",seconds(g.nextSend)); }
            Instant now=clock.instant();
            g.nextSend=now.plusSeconds(60);
            g.challenge=new Challenge(Base64.getUrlEncoder().withoutPadding().encodeToString(random(32)),email,a.passwordHash(),salt,hash(salt,otp),now.plusSeconds(300));
            return new Start(g.challenge.id(),mask(email),g.challenge.expiresAt(),g.nextSend);
        }
    }
    public Map<String,Object> verifyEmail(int userId, String id, String code) throws Exception {
        Gate g=gate(userId,"email");
        synchronized(g) {
            Challenge c=verify(g,id,code);
            if (!clean(account(userId).email()).equals(c.email())) { g.challenge=null; throw new Failure(409,"Email đã thay đổi. Hãy yêu cầu mã mới."); }
            repository.verifyEmail(userId,c.email());
            g.challenge=null; g.failures=0;
            return profile(userId);
        }
    }
    public void changePassword(int userId, String id, String code, String newPassword) throws Exception {
        if (newPassword==null || newPassword.length()<8 || newPassword.getBytes(StandardCharsets.UTF_8).length>72) throw new Failure(400,"Mật khẩu mới phải có ít nhất 8 ký tự và không quá 72 byte.");
        Gate g=gate(userId,"password");
        synchronized(g) {
            Challenge c=verify(g,id,code);
            Account a=account(userId);
            if (!a.emailVerified() || !clean(a.email()).equals(c.email()) || !Objects.equals(a.passwordHash(),c.passwordHash())) { g.challenge=null; throw new Failure(409,"Thông tin bảo mật đã thay đổi. Hãy yêu cầu mã mới."); }
            if (SecurityUtil.verifyPassword(newPassword,a.passwordHash())) throw new Failure(400,"Mật khẩu mới phải khác mật khẩu hiện tại.");
            repository.password(userId,c.passwordHash(),c.email(),SecurityUtil.hashPassword(newPassword));
            g.challenge=null; g.failures=0;
        }
    }
    private Challenge verify(Gate g, String id, String code) throws Failure {
        unlocked(g);
        Challenge c=g.challenge;
        if (c==null || id==null || !c.id().equals(id)) throw new Failure(410,"Phiên xác minh không còn hiệu lực. Hãy yêu cầu mã mới.");
        if (!clock.instant().isBefore(c.expiresAt())) throw new Failure(422,"Mã đã hết hạn. Hãy gửi lại mã mới.");
        if (code==null || !code.matches("[0-9]{6}") || !MessageDigest.isEqual(c.hash(),hash(c.salt(),code))) failed(g,"Mã xác thực không đúng. Vui lòng kiểm tra lại.");
        return c;
    }
    private static String clean(String s) { return s==null?"":s.trim().toLowerCase(Locale.ROOT); }
    private static String mask(String email) { int at=email.indexOf('@'); return email.substring(0,1)+"***"+email.substring(at); }
    private static byte[] random(int size) { byte[] bytes=new byte[size]; RANDOM.nextBytes(bytes); return bytes; }
    private static byte[] hash(byte[] salt,String otp) { try {var d=MessageDigest.getInstance("SHA-256"); d.update(salt); return d.digest(otp.getBytes(StandardCharsets.UTF_8));} catch(Exception e){throw new IllegalStateException(e);} }
}
