package quanlykhachsan.backend.booking;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import quanlykhachsan.backend.room.*;
import quanlykhachsan.backend.pricing.*;
import quanlykhachsan.backend.utils.*;

/** Booking, inventory, initial ledger and durable replay commit as one transaction. */
public final class PublicBookingService {
    public Result reserve(String key,Request request,int customerId,String channel) throws Exception {
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            (channel+"|"+customerId+"|"+request).getBytes(StandardCharsets.UTF_8)));
        FolioDao locks=new FolioDao();
        try(Connection c=DatabaseConnection.getConnection()) {
            locks.acquireLock(c,key);
            try {
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                c.setAutoCommit(false);
                // The advisory lock serializes this key. FOR UPDATE on a missing
                // key would lock an index gap and deadlock independent bookings.
                FolioDao.PaymentReplay replay;
                try(PreparedStatement q=c.prepareStatement("SELECT request_hash,response_body,status_code FROM idempotency_keys WHERE id=?")) {
                    q.setString(1,key);try(ResultSet r=q.executeQuery()){replay=r.next()?new FolioDao.PaymentReplay(r.getString(1),r.getString(2),r.getInt(3)):null;}
                }
                if(replay!=null){c.rollback();if(!replay.requestHash().equals(hash))throw new ConflictException("Idempotency-Key was reused with different data");return new Result(replay.statusCode(),replay.responseBody());}
                RoomAllocationService.reserveForStay(c,request.roomId(),request.version(),request.guestCount(),request.checkIn(),request.checkOut());
                String roomNumber;int roomType;
                try(PreparedStatement q=c.prepareStatement("SELECT room_number,room_type_id FROM rooms WHERE id=?")) {
                    q.setInt(1,request.roomId());try(ResultSet r=q.executeQuery()){if(!r.next())throw new RoomUnavailableException();roomNumber=r.getString(1);roomType=r.getInt(2);}
                }
                var quote=new DynamicPricingEngine().quote(c,roomType,new PortalValidation.DateRange(request.checkIn(),request.checkOut()));
                var vouchers=new quanlykhachsan.backend.promotion.VoucherService();
                quanlykhachsan.backend.promotion.VoucherService.Discount discount=null;
                if(request.voucherCode()!=null && !request.voucherCode().isBlank()) {
                    discount=vouchers.evaluate(c,request.voucherCode(),customerId,roomType,new PortalValidation.DateRange(request.checkIn(),request.checkOut()),quote,true);
                    quote=new DynamicPricingEngine.Quote(quote.room_type_id(),quote.base_price(),quote.nightly_rates(),discount.room_subtotal(),discount.service_charge(),discount.vat(),discount.total_amount());
                }
                if(request.expectedTotal()!=null && request.expectedTotal().compareTo(quote.total_amount())!=0)
                    throw new ConflictException("Booking quote changed");
                if(quote.total_amount().compareTo(new BigDecimal("99999999.99"))>0)throw new IllegalArgumentException("Stay exceeds booking amount limit");
                int bookingId;
                try(PreparedStatement q=c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type) VALUES (?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    q.setInt(1,customerId);q.setInt(2,request.roomId());q.setTimestamp(3,Timestamp.valueOf(request.checkIn().atTime(14,0)));q.setTimestamp(4,Timestamp.valueOf(request.checkOut().atTime(12,0)));q.setBigDecimal(5,quote.total_amount());q.setString(6,"pending");q.setString(7,channel);q.executeUpdate();
                    try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Booking ID missing");bookingId=r.getInt(1);}
                }
                long folioId;
                try(PreparedStatement q=c.prepareStatement("INSERT INTO folios(booking_id,folio_type,balance,status,code) VALUES (?,'master',?,'open',?)",Statement.RETURN_GENERATED_KEYS)) {
                    q.setInt(1,bookingId);q.setBigDecimal(2,quote.total_amount());q.setString(3,"FOLIO-MASTER-B"+bookingId);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Folio ID missing");folioId=r.getLong(1);}
                }
                if(discount!=null)vouchers.record(c,discount,customerId,bookingId,folioId);
                post(c,folioId,bookingId,"room_rate",quote.room_subtotal(),"BOOKING-ROOM-"+bookingId);
                post(c,folioId,bookingId,"service_charge",quote.service_charge(),"BOOKING-SERVICE-"+bookingId);
                post(c,folioId,bookingId,"vat",quote.vat(),"BOOKING-VAT-"+bookingId);
                JsonObject data=new JsonObject();data.addProperty("booking_id",bookingId);data.addProperty("bookingId",bookingId);data.addProperty("booking_code","BKG-"+(channel.equals("Web")?"WEB":"WALKIN")+"-"+request.checkIn().getYear()+"-"+bookingId);data.addProperty("room_number",roomNumber);data.addProperty("room_version",request.version()+1);data.addProperty("status","pending");
                JsonObject folio=new JsonObject();folio.addProperty("id",folioId);folio.addProperty("code","FOLIO-MASTER-B"+bookingId);folio.addProperty("balance",quote.total_amount());folio.addProperty("status","open");data.add("folio",folio);
                String bin=System.getenv("BANK_BIN"),account=System.getenv("BANK_ACCOUNT_NUMBER"),holder=System.getenv("BANK_ACCOUNT_HOLDER");
                if(bin!=null&&account!=null&&holder!=null){
                    var qr=new VietQrGenerator().generate(bin,account,quote.total_amount(),"FOLIO-MASTER-B"+bookingId);
                    JsonObject payment=new JsonObject();payment.addProperty("qr_image_base64",qr.qr_image_base64());payment.addProperty("beneficiary_bank",bin);payment.addProperty("account_number",account);payment.addProperty("account_holder",holder);payment.addProperty("reference_code","FOLIO-MASTER-B"+bookingId);data.add("payment",payment);
                }
                String body=ApiResponseUtil.successWithData(data);
                try(PreparedStatement q=c.prepareStatement("INSERT INTO idempotency_keys(id,request_hash,response_body,status_code) VALUES (?,?,?,201)")){q.setString(1,key);q.setString(2,hash);q.setString(3,body);q.executeUpdate();}
                c.commit();PricingQuoteCache.invalidate();return new Result(201,body);
            } catch(Exception ex){c.rollback();throw ex;}
            finally {c.setAutoCommit(true);locks.releaseLock(c,key);}
        }
    }
    private static void post(Connection c,long folio,int booking,String type,BigDecimal amount,String reference)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'debit',?,'Confirmed stay charge',?,business_date,? FROM hotel_business_state WHERE id=1")) {
            q.setLong(1,folio);q.setString(2,type);q.setBigDecimal(3,amount);q.setString(4,reference);if(q.executeUpdate()!=1)throw new SQLException("Hotel business date missing");
        }
    }
    public record Request(String name,String phone,String email,int roomId,int version,LocalDate checkIn,LocalDate checkOut,String voucherCode,int guestCount,BigDecimal expectedTotal) {
        public Request(String name,String phone,String email,int roomId,int version,LocalDate in,LocalDate out,String voucherCode,int guestCount){this(name,phone,email,roomId,version,in,out,voucherCode,guestCount,null);}
        public Request(String name,String phone,String email,int roomId,int version,LocalDate in,LocalDate out){this(name,phone,email,roomId,version,in,out,null,1,null);}
        public Request(String name,String phone,String email,int roomId,int version,LocalDate in,LocalDate out,String voucherCode){this(name,phone,email,roomId,version,in,out,voucherCode,1,null);}
    }
    public record Result(int status,String body) { }
    public static final class ConflictException extends IllegalStateException {
        public ConflictException(String message) { super(message); }
    }
}
