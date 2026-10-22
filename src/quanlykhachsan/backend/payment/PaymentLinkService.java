package quanlykhachsan.backend.payment;

import com.google.gson.JsonObject;
import java.sql.*;
import java.time.*;
import quanlykhachsan.backend.booking.*;

/** Persist before external I/O. A process crash retries the same provider order. */
public final class PaymentLinkService {
    private final PaymentGateway gateway;
    private final PaymentGateway.Beneficiary config;
    public PaymentLinkService(){this(defaultGateway());}
    public PaymentLinkService(PaymentGateway g){gateway=g;config=g.beneficiary();}
    private static PaymentGateway defaultGateway(){
        String provider=System.getenv().getOrDefault("PAYMENT_QR_PROVIDER","payos");
        return switch(provider){case "payos"->new PayOsGateway(PayOsConfig.environment());default->throw new IllegalStateException("QR provider adapter is not installed");};
    }
    public JsonObject link(Connection c,long folio)throws Exception {
        if(!c.getAutoCommit())throw new IllegalStateException("QR creation requires its own database connection");
        gateway.requireConfigured();var locks=new FolioDao();String key="payment-link:"+folio;locks.acquireLock(c,key);
        try {
            long order=0;String reference=null,payload=null;LocalDateTime expires=null;java.math.BigDecimal amount=null;
            c.setAutoCommit(false);
            try {
                var f=locks.lockFolio(c,folio);if(f==null||f.balance().signum()<=0)throw new IllegalStateException("NO_PAYABLE_BALANCE");
                amount=f.balance();amount.longValueExact();
                try(var q=c.prepareStatement("SELECT order_code,reference_code,qr_payload,expires_at FROM payment_links WHERE folio_id=? AND provider=? AND state IN ('CREATING','PENDING') AND amount=? AND account_number=? AND bank_bin=? AND account_name=? AND expires_at>CURRENT_TIMESTAMP ORDER BY order_code DESC LIMIT 1")){
                    q.setLong(1,folio);q.setString(2,gateway.provider());q.setBigDecimal(3,amount);q.setString(4,config.account());q.setString(5,config.bin());q.setString(6,config.holder());try(var r=q.executeQuery()){if(r.next()){order=r.getLong(1);reference=r.getString(2);payload=r.getString(3);expires=r.getObject(4,LocalDateTime.class);}}
                }
                if(order==0){
                    reference="PENDING";
                    expires=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withNano(0).plusMinutes(15);
                    try(var q=c.prepareStatement("INSERT INTO payment_links(folio_id,provider,amount,reference_code,account_number,account_name,bank_bin,expires_at) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                        q.setLong(1,folio);q.setString(2,gateway.provider());q.setBigDecimal(3,amount);q.setString(4,reference);q.setString(5,config.account());q.setString(6,config.holder());q.setString(7,config.bin());q.setObject(8,expires);q.executeUpdate();try(var r=q.getGeneratedKeys()){r.next();order=r.getLong(1);}
                    }
                    String code=Long.toString(order,36).toUpperCase(java.util.Locale.ROOT);if(code.length()>7)throw new IllegalStateException("Payment reference range exhausted");
                    reference="AH"+"0".repeat(7-code.length())+code;
                    try(var q=c.prepareStatement("UPDATE payment_links SET reference_code=? WHERE order_code=?")){q.setString(1,reference);q.setLong(2,order);q.executeUpdate();}
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            if(payload==null){
                JsonObject data=gateway.createLink(order,amount.longValueExact(),reference,expires.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toEpochSecond());
                payload=data.get("qrCode").getAsString();
                try(var q=c.prepareStatement("UPDATE payment_links SET payment_link_id=?,qr_payload=?,checkout_url=?,state='PENDING' WHERE order_code=?")){
                    q.setString(1,data.get("paymentLinkId").getAsString());q.setString(2,payload);q.setString(3,data.get("checkoutUrl").getAsString());q.setLong(4,order);q.executeUpdate();
                }
            }
            JsonObject result=new JsonObject();result.addProperty("provider",gateway.provider());result.addProperty("folio_id",folio);result.addProperty("order_code",order);result.addProperty("reference_code",reference);result.addProperty("amount",amount);result.addProperty("currency","VND");result.addProperty("beneficiary_bank",config.bin());result.addProperty("account_number",config.account());result.addProperty("account_holder",config.holder());result.addProperty("qr_code_string",payload);result.addProperty("qr_image_base64",VietQrGenerator.encodePng(payload));result.addProperty("expires_in_seconds",Math.max(0,Duration.between(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),expires).toSeconds()));return result;
        }finally{locks.releaseLock(c,key);}
    }
}
