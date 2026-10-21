package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.time.*;
import java.time.format.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.customer.GuestIngestionService;
import quanlykhachsan.backend.room.OptimisticLockException;

public final class PublicBookingController implements HttpHandler {
    private final boolean walkIn;
    @FunctionalInterface interface Guests { int capture(Integer owner, String name, String phone, String email, String identity) throws Exception; }
    @FunctionalInterface interface Reservations { PublicBookingService.Result reserve(String key, PublicBookingService.Request request, int customer, String channel) throws Exception; }
    @FunctionalInterface interface Owners { Integer find(int userId, String phone) throws Exception; }
    private final Guests guests;
    private final Reservations reservations;
    private final Owners owners;
    public PublicBookingController(){this(false);}
    public PublicBookingController(boolean walkIn){
        this(walkIn, (owner,name,phone,email,identity) -> {
            var service = new GuestIngestionService();
            return owner == null ? service.ingest(name,phone,email,identity) : service.ingestOwned(owner,name,phone,email,identity);
        }, new PublicBookingService()::reserve, PublicBookingController::findOwner);
    }
    PublicBookingController(boolean walkIn, Guests guests, Reservations reservations, Owners owners) {
        this.walkIn=walkIn;this.guests=guests;this.reservations=reservations;this.owners=owners;
    }
    private static Integer findOwner(int userId, String phone) throws Exception {
        try (var c=DatabaseConnection.getConnection(); var q=c.prepareStatement("SELECT c.id,c.phone FROM users u JOIN customers c ON c.id=u.customer_id WHERE u.id=?")) {
            q.setInt(1,userId);
            try(var row=q.executeQuery()) {
                if(!row.next() || !phone.equals(PortalValidation.phone(row.getString(2)))) return null;
                return row.getInt(1);
            }
        }
    }
    @Override public void handle(HttpExchange e)throws IOException {
        String expected=walkIn?"/api/v1/bookings/walk-in":"/api/v1/bookings/public";
        if(!e.getRequestURI().getPath().equals(expected)){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("POST")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        String operation="public-booking/validation";
        try {
            String role=String.valueOf(e.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
            boolean account=java.util.Set.of("customer","3").contains(role);
            if(walkIn && !java.util.Set.of("front_desk","admin").contains(role))throw new SecurityException();
            if(!walkIn && e.getAttribute("role")!=null && !role.equals("guest") && !account)throw new SecurityException();
            String key=e.getRequestHeaders().getFirst("Idempotency-Key");
            if(key==null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException();
            var b=PortalValidation.body(e);String name=PortalValidation.text(b,"customer_name",100,true);
            String phone=PortalValidation.phone(PortalValidation.text(b,"phone",20,true));String email=PortalValidation.email(PortalValidation.text(b,"email",100,false));
            String identity=PortalValidation.text(b,"identity_card",20,walkIn);
            if(e.getAttribute("role")!=null && role.equals("guest") && e.getAttribute("userId")!=null && !phone.equals(e.getAttribute("phone")))throw new SecurityException();
            String voucher=PortalValidation.text(b,"voucher_code",50,false);
            var format=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
            LocalDateTime in=LocalDateTime.parse(PortalValidation.text(b,"check_in_date",19,true),format),out=LocalDateTime.parse(PortalValidation.text(b,"check_out_date",19,true),format);
            if(!in.toLocalTime().equals(LocalTime.of(14,0)) || !out.toLocalTime().equals(LocalTime.NOON))throw new IllegalArgumentException();
            var range=PortalValidation.range(in.toLocalDate().toString(),out.toLocalDate().toString());
            if(walkIn && !range.checkIn().equals(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))throw new IllegalArgumentException();
            int roomId=PortalValidation.integer(b,"room_id",1),roomVersion=PortalValidation.integer(b,"room_version",0);
            int guestCount=b.has("guest_count")?PortalValidation.integer(b,"guest_count",1):1;
            operation="public-booking/customer";
            Integer owner=null;
            if(!walkIn && account) {
                if(!(e.getAttribute("userId") instanceof Number user) || user.intValue()<1)throw new SecurityException();
                owner=owners.find(user.intValue(),phone);
                if(owner==null)throw new SecurityException();
            }
            int customer=guests.capture(owner,name,phone,email,identity);
            if(owner!=null && owner!=customer)throw new SecurityException();
            if(role.equals("guest") && e.getAttribute("userId")!=null && (!(e.getAttribute("customer_id") instanceof Number) || ((Number)e.getAttribute("customer_id")).intValue()!=customer))throw new SecurityException();
            operation="public-booking/reserve";
            var result=reservations.reserve(key.toLowerCase(java.util.Locale.ROOT),new PublicBookingService.Request(name,phone,email,roomId,roomVersion,range.checkIn(),range.checkOut(),voucher,guestCount),customer,walkIn?"Walk-in":"Web");
            ApiResponseUtil.write(e,result.status(),result.body());
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Role or guest identity is not authorized"));}
        catch(IllegalArgumentException | DateTimeException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid booking data or Idempotency-Key"));}
        catch(OptimisticLockException | PublicBookingService.ConflictException | GuestIngestionService.IdentityConflictException ex){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Room or request data changed; please check availability again"));}
        catch(Exception ex){e.getResponseHeaders().set("X-Booking-Error-Id",BookingFailureLog.record(operation,ex));ApiResponseUtil.write(e,503,ApiResponseUtil.error("Booking is unavailable; please retry with the same Idempotency-Key or contact the Front Desk"));}
    }
}
