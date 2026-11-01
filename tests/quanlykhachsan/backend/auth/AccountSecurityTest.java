package quanlykhachsan.backend.auth;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import quanlykhachsan.backend.utils.SecurityUtil;
import static org.junit.jupiter.api.Assertions.*;

class AccountSecurityTest {
    @Test void verifiedEmailDoesNotSendAnotherVerificationOtp() throws Exception {
        var f = new Fixture(); f.verified();
        assertEquals(409, status(() -> f.service.beginEmail(1)));
        assertEquals(0, f.codes.size());
    }
    static final String OLD="Old-password-123", NEXT="New-password-456";
    static final String HASH=SecurityUtil.hashPassword(OLD);
    static class Time extends Clock {
        Instant now=Instant.parse("2026-10-22T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
        void advance(int seconds){now=now.plusSeconds(seconds);}
    }
    static class Store implements AccountSecurityService.Repository {
        Map<Integer,AccountSecurityService.Account> rows=new HashMap<>();
        boolean fail;
        Store(){ rows.put(1,new AccountSecurityService.Account(1,"synthetic","Khách hàng","0900000000","guest@example.invalid",null,HASH)); }
        public AccountSecurityService.Account read(int id){return rows.get(id);}
        public AccountSecurityService.Account profile(int id,String name,String phone,String email){
            var a=read(id); var result=new AccountSecurityService.Account(id,a.username(),name,phone,email,a.verifiedEmail(),a.passwordHash()); rows.put(id,result); return result;
        }
        public void verifyEmail(int id,String expected) throws Exception {
            if(fail)throw new java.sql.SQLException("Synthetic outage");
            var a=read(id);rows.put(id,new AccountSecurityService.Account(id,a.username(),a.fullName(),a.phone(),a.email(),expected,a.passwordHash()));
        }
        public void password(int id,String expectedHash,String email,String newHash) throws Exception {
            if(fail)throw new java.sql.SQLException("Synthetic outage");
            var a=read(id);assertEquals(expectedHash,a.passwordHash());assertEquals(email,a.verifiedEmail());
            rows.put(id,new AccountSecurityService.Account(id,a.username(),a.fullName(),a.phone(),a.email(),a.verifiedEmail(),newHash));
        }
    }
    static class Fixture {
        Time time=new Time(); Store store=new Store(); List<String> codes=new ArrayList<>(); boolean mailFail; int mailDelay;
        AccountSecurityService service=new AccountSecurityService(time,store,(email,code,purpose)->{
            assertEquals(store.read(1).email(),email);assertTrue(code.matches("[0-9]{6}"));
            if(mailFail)throw new Exception("Synthetic SMTP failure"); codes.add(code);time.advance(mailDelay);
        });
        String code(){return codes.get(codes.size()-1);}
        void verified()throws Exception {store.verifyEmail(1,store.read(1).email());}
    }
    static int status(org.junit.jupiter.api.function.Executable operation){return assertThrows(AccountSecurityService.Failure.class,operation).status;}

    @Test void verifiesOnceAndNeverExposesSecrets()throws Exception {
        var f=new Fixture();var start=f.service.beginEmail(1);
        assertEquals("g***@example.invalid",start.maskedEmail());
        assertEquals(422,status(()->f.service.verifyEmail(1,start.challengeId(),"not-otp")));
        var profile=f.service.verifyEmail(1,start.challengeId(),f.code());
        assertEquals(true,profile.get("emailVerified"));assertTrue(profile.containsKey("avatarUrl"));assertNull(profile.get("avatarUrl"));
        assertEquals(410,status(()->f.service.verifyEmail(1,start.challengeId(),f.code())));
        assertFalse(f.service.profile(1).containsKey("passwordHash"));
    }
    @Test void expiryAndResendReplaceOnlySuccessfullyDeliveredChallenges()throws Exception {
        var f=new Fixture();var first=f.service.beginEmail(1);String oldCode=f.code();
        assertEquals(429,status(()->f.service.beginEmail(1)));
        f.time.advance(61);f.mailFail=true;
        assertEquals(503,status(()->f.service.beginEmail(1)));
        f.service.verifyEmail(1,first.challengeId(),oldCode);
        f.service.saveProfile(1,"Khách hàng","0900000000","changed@example.invalid");
        f.mailFail=false;f.time.advance(61);var second=f.service.beginEmail(1);
        assertEquals(410,status(()->f.service.verifyEmail(1,first.challengeId(),oldCode)));
        f.time.advance(300);
        assertEquals(422,status(()->f.service.verifyEmail(1,second.challengeId(),f.code())));
        var third=f.service.beginEmail(1);f.service.verifyEmail(1,third.challengeId(),f.code());
    }
    @Test void ttlStartsAfterSmtpCompletes()throws Exception {
        var f=new Fixture();f.mailDelay=400;var start=f.service.beginEmail(1);
        assertEquals(f.time.instant().plusSeconds(300),start.expiresAt());
        f.service.verifyEmail(1,start.challengeId(),f.code());
    }
    @Test void wrongCodesLockEvenAcrossResendsAndHourlySendBudgetIsBounded()throws Exception {
        var f=new Fixture();var start=f.service.beginEmail(1);
        for(int i=0;i<4;i++)assertEquals(422,status(()->f.service.verifyEmail(1,start.challengeId(),"abcdef")));
        assertEquals(429,status(()->f.service.verifyEmail(1,start.challengeId(),"abcdef")));
        assertEquals(429,status(()->f.service.beginEmail(1)));
        f.time.advance(601);var fresh=f.service.beginEmail(1);f.service.verifyEmail(1,fresh.challengeId(),f.code());
        f.service.saveProfile(1,"Khách hàng","0900000000","changed@example.invalid");
        for(int i=2;i<10;i++){f.time.advance(61);f.service.beginEmail(1);}
        f.time.advance(61);assertEquals(429,status(()->f.service.beginEmail(1)));
    }
    @Test void codesCannotCrossAccountsPurposesOrEmailChanges()throws Exception {
        var f=new Fixture();var email=f.service.beginEmail(1);String code=f.code();
        assertEquals(410,status(()->f.service.verifyEmail(2,email.challengeId(),code)));
        assertEquals(410,status(()->f.service.changePassword(1,email.challengeId(),code,NEXT)));
        f.service.saveProfile(1,"Khách hàng","0900000000","new@example.invalid");
        assertEquals(409,status(()->f.service.verifyEmail(1,email.challengeId(),code)));
        assertFalse(f.store.read(1).emailVerified());
    }
    @Test void passwordRequiresVerifiedEmailCurrentPasswordAndOtpBeforeWrite()throws Exception {
        var f=new Fixture();assertEquals(409,status(()->f.service.beginPassword(1,OLD)));
        f.verified();assertEquals(422,status(()->f.service.beginPassword(1,"incorrect")));
        assertEquals(0,f.codes.size());var start=f.service.beginPassword(1,OLD);
        assertEquals(HASH,f.store.read(1).passwordHash());
        assertEquals(422,status(()->f.service.changePassword(1,start.challengeId(),"abcdef",NEXT)));
        assertEquals(400,status(()->f.service.changePassword(1,start.challengeId(),f.code(),OLD)));
        assertEquals(400,status(()->f.service.changePassword(1,start.challengeId(),f.code(),"short")));
        f.service.changePassword(1,start.challengeId(),f.code(),NEXT);
        assertTrue(SecurityUtil.verifyPassword(NEXT,f.store.read(1).passwordHash()));
        assertEquals(410,status(()->f.service.changePassword(1,start.challengeId(),f.code(),NEXT)));
    }
    @Test void passwordChallengeCannotSurviveAnIdentityOrCredentialChange()throws Exception {
        var f=new Fixture();f.verified();var start=f.service.beginPassword(1,OLD);
        f.store.password(1,HASH,f.store.read(1).email(),SecurityUtil.hashPassword("Changed-elsewhere"));
        assertEquals(409,status(()->f.service.changePassword(1,start.challengeId(),f.code(),NEXT)));
    }
    @Test void storageFailureDoesNotConsumeCorrectCodes()throws Exception {
        var f=new Fixture();var email=f.service.beginEmail(1);f.store.fail=true;
        assertThrows(java.sql.SQLException.class,()->f.service.verifyEmail(1,email.challengeId(),f.code()));
        f.store.fail=false;f.service.verifyEmail(1,email.challengeId(),f.code());
        var password=f.service.beginPassword(1,OLD);f.store.fail=true;
        assertThrows(java.sql.SQLException.class,()->f.service.changePassword(1,password.challengeId(),f.code(),NEXT));
        f.store.fail=false;f.service.changePassword(1,password.challengeId(),f.code(),NEXT);
    }
    @Test void profileValidationAndVerificationFollowCanonicalAddress()throws Exception {
        var f=new Fixture();f.verified();
        var saved=f.service.saveProfile(1,"Tên mới","0901234567"," GUEST@EXAMPLE.INVALID ");
        assertEquals(true,saved.get("emailVerified"));assertEquals("guest@example.invalid",saved.get("email"));
        assertEquals(400,status(()->f.service.saveProfile(1,"Tên","garbage","a@example.invalid")));
        assertEquals(400,status(()->f.service.saveProfile(1,"Tên","","a\r\n@example.invalid")));
        assertEquals(false,f.service.saveProfile(1,"Tên mới","","other@example.invalid").get("emailVerified"));
    }
}
