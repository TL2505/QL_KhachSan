package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.time.*;
import java.time.format.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.customer.GuestIngestionService;
import quanlykhachsan.backend.room.OptimisticLockException;
import quanlykhachsan.backend.room.RentalType;
import quanlykhachsan.backend.room.StayWindow;

public final class PublicBookingController implements HttpHandler {
    private final boolean walkIn;
    public PublicBookingController(){this(false);}
    public PublicBookingController(boolean walkIn){this.walkIn=walkIn;}
    @Override public void handle(HttpExchange e)throws IOException {
        String expected=walkIn?"/api/v1/bookings/walk-in":"/api/v1/bookings/public";
        if(!e.getRequestURI().getPath().equals(expected)){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("POST")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try {
            String role=String.valueOf(e.getAttribute("role"));
            String normalizedRole=role.toLowerCase(java.util.Locale.ROOT);
            if(walkIn && !java.util.Set.of("front_desk","staff","admin").contains(normalizedRole))throw new SecurityException();
            if(!walkIn && e.getAttribute("role")!=null && !java.util.Set.of("guest","customer","3").contains(normalizedRole))throw new SecurityException();
            String key=e.getRequestHeaders().getFirst("Idempotency-Key");
            if(key==null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException();
            var b=PortalValidation.body(e);String name=PortalValidation.text(b,"customer_name",100,true);
            String phone=PortalValidation.phone(PortalValidation.text(b,"phone",20,true));String email=PortalValidation.email(PortalValidation.text(b,"email",100,false));
            String identity=PortalValidation.text(b,"identity_card",20,walkIn);
            boolean authenticatedGuest=e.getAttribute("userId") instanceof Number
                && java.util.Set.of("guest","customer","3").contains(normalizedRole);
            if(authenticatedGuest && e.getAttribute("phone") instanceof String claimPhone && !phone.equals(claimPhone))throw new SecurityException();
            String voucher=PortalValidation.text(b,"voucher_code",50,false);
            var format=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
            LocalDateTime in=LocalDateTime.parse(PortalValidation.text(b,"check_in_date",19,true),format),out=LocalDateTime.parse(PortalValidation.text(b,"check_out_date",19,true),format);
            RentalType requestedType=RentalType.parse(PortalValidation.text(b,"rental_type",20,false));
            String paymentChoice=walkIn?"counter":PortalValidation.text(b,"payment_choice",16,false);
            if(paymentChoice==null)paymentChoice="counter";
            paymentChoice=paymentChoice.toLowerCase(java.util.Locale.ROOT);
            if(!java.util.Set.of("counter","qr").contains(paymentChoice))throw new IllegalArgumentException();
            StayWindow stay=StayWindow.resolve(requestedType,in,out);
            if(walkIn && !stay.checkIn().toLocalDate().equals(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))throw new IllegalArgumentException();
            int roomId=PortalValidation.integer(b,"room_id",1),roomVersion=PortalValidation.integer(b,"room_version",0);
            int guestCount=b.has("guest_count")?PortalValidation.integer(b,"guest_count",1):1;
            java.math.BigDecimal expectedTotal=null;
            if(b.has("quoted_total")) {
                if(!b.get("quoted_total").isJsonPrimitive() || !b.getAsJsonPrimitive("quoted_total").isNumber())throw new IllegalArgumentException();
                expectedTotal=b.get("quoted_total").getAsBigDecimal();
                if(expectedTotal.signum()<=0 || expectedTotal.scale()>2)throw new IllegalArgumentException();
            }
            int customer=new GuestIngestionService().ingest(name,phone,email,identity);
            if(authenticatedGuest && (!(e.getAttribute("customer_id") instanceof Number) || ((Number)e.getAttribute("customer_id")).intValue()!=customer))throw new SecurityException();
            var request=new PublicBookingService.Request(name,phone,email,roomId,roomVersion,
                stay.checkIn().toLocalDate(),stay.checkOut().toLocalDate(),voucher,guestCount,expectedTotal,
                stay.rentalType(),stay.checkIn().toLocalTime(),stay.checkOut().toLocalTime(),paymentChoice);
            var result=new PublicBookingService().reserve(key.toLowerCase(java.util.Locale.ROOT),request,customer,walkIn?"Walk-in":"Web");
            ApiResponseUtil.write(e,result.status(),result.body());
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Role or guest identity is not authorized"));}
        catch(IllegalArgumentException | DateTimeException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid booking data or Idempotency-Key"));}
        catch(OptimisticLockException | IllegalStateException ex){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Room or request data changed; please check availability again"));}
        catch(Exception ex){System.err.println("Public booking failed: "+ex.getClass().getSimpleName());ApiResponseUtil.write(e,503,ApiResponseUtil.error("Booking is unavailable; please retry with the same Idempotency-Key or contact the Front Desk"));}
    }
}
