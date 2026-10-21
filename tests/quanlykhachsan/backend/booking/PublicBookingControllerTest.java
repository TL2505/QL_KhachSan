package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.customer.GuestIngestionService;
import quanlykhachsan.backend.room.OptimisticLockException;
import quanlykhachsan.backend.utils.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP/JWT boundary tests with isolated collaborators; no application DB connection. */
class PublicBookingControllerTest {
    private static final String KEY="517c2443-b912-477e-8c5d-fa1a225732ef";
    private static String body() {
        LocalDate date=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
        return "{\"customer_name\":\"Isolated Guest\",\"phone\":\"0901234567\",\"email\":\"guest@example.test\",\"identity_card\":\"TEST12345\",\"room_id\":3,\"room_version\":4,\"guest_count\":2,\"check_in_date\":\""+date+" 14:00:00\",\"check_out_date\":\""+date.plusDays(1)+" 12:00:00\"}";
    }
    private static HttpResponse<String> request(PublicBookingController controller,String role,String key,String body,String method)throws Exception {
        System.setProperty("hotel.jwt.secret","isolated-public-booking-test-secret-at-least-32-bytes");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var context=server.createContext("/api/v1/bookings/public",controller);
        context.getFilters().add(new CorsFilter());context.getFilters().add(new JwtFilter());server.start();
        try {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/bookings/public"))
                .header("Content-Type","application/json").header("X-User-Role","guest");
            if(role!=null)builder.header("Authorization","Bearer "+JwtTokenUtil.issueToken(17,"isolated-guest",role));
            if(key!=null)builder.header("Idempotency-Key",key);
            return HttpClient.newHttpClient().send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        } finally {server.stop(0);}
    }
    private static PublicBookingController success(AtomicInteger captures) {
        return new PublicBookingController(false,(owner,name,phone,email,identity)->{
            captures.incrementAndGet();assertEquals("0901234567",phone);assertEquals("TEST12345",identity);return owner==null?7:owner;
        },(key,request,customer,channel)->{
            assertEquals(KEY,key);assertEquals(7,customer);assertEquals("Web",channel);
            assertEquals(2,request.guestCount());assertEquals(4,request.version());
            return new PublicBookingService.Result(201,"{\"status\":\"success\",\"data\":{\"booking_id\":42}}");
        },(user,phone)->{assertEquals(17,user);return 7;});
    }
    @Test void publicAndVerifiedCustomerSessionsUseValidatedRequestAndOwnProfile()throws Exception {
        var calls=new AtomicInteger();var controller=success(calls);
        assertEquals(201,request(controller,null,KEY,body(),"POST").statusCode());
        for(String role:List.of("CUSTOMER","customer","3"))assertEquals(201,request(controller,role,KEY,body(),"POST").statusCode());
        assertEquals(4,calls.get());
    }
    @Test void unlinkedOrDifferentPhoneAccountIsDeniedBeforeAnyWrite()throws Exception {
        var writes=new AtomicInteger();
        var controller=new PublicBookingController(false,(o,n,p,e,i)->{writes.incrementAndGet();return 7;},(k,r,c,h)->{throw new AssertionError();},(u,p)->null);
        assertEquals(403,request(controller,"CUSTOMER",KEY,body(),"POST").statusCode());assertEquals(0,writes.get());
    }
    @Test void invalidInputMethodAndPrivilegedAccountsNeverReachWriters()throws Exception {
        var writes=new AtomicInteger();var controller=success(writes);
        assertEquals(400,request(controller,null,null,body(),"POST").statusCode());
        assertEquals(400,request(controller,null,KEY,body().replace("14:00:00","15:00:00"),"POST").statusCode());
        for(String role:List.of("ADMIN","STAFF","housekeeping"))assertEquals(403,request(controller,role,KEY,body(),"POST").statusCode());
        assertEquals(405,request(controller,null,KEY,"","GET").statusCode());assertEquals(0,writes.get());
    }
    @Test void configurationAndSqlFailuresAreUnavailableWithSafeCorrelationNotRoomConflicts()throws Exception {
        for(Exception failure:List.of(new IllegalStateException("AES_VAULT_KEY is required PRIVATE_VALUE"),new SQLException("PRIVATE_SQL_LITERAL","23000",4025))) {
            var controller=new PublicBookingController(false,(o,n,p,e,i)->{throw failure;},(k,r,c,h)->{throw new AssertionError();},(u,p)->7);
            var response=request(controller,null,KEY,body(),"POST");assertEquals(503,response.statusCode());
            assertTrue(response.headers().firstValue("X-Booking-Error-Id").orElseThrow().matches("[0-9a-f-]{36}"));
            assertTrue(response.headers().firstValue("Access-Control-Expose-Headers").orElseThrow().contains("X-Booking-Error-Id"));
            assertFalse(response.body().contains("PRIVATE"));
        }
    }
    @Test void reservationFailureIsUnavailableAndBusinessConflictsRemain409()throws Exception {
        var failed=new PublicBookingController(false,(o,n,p,e,i)->7,(k,r,c,h)->{throw new SQLException("PRIVATE","08001",1045);},(u,p)->7);
        assertEquals(503,request(failed,null,KEY,body(),"POST").statusCode());
        for(Exception failure:List.of(new OptimisticLockException(),new PublicBookingService.ConflictException("changed"),new GuestIngestionService.IdentityConflictException())) {
            var conflict=new PublicBookingController(false,(o,n,p,e,i)->7,(k,r,c,h)->{throw failure;},(u,p)->7);
            assertEquals(409,request(conflict,null,KEY,body(),"POST").statusCode());
        }
    }
}
