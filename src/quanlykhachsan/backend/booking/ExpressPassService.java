package quanlykhachsan.backend.booking;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.itextpdf.text.pdf.BarcodeQRCode;

/** Offline, short-lived pass. A pass assists staff; it never changes booking state. */
public final class ExpressPassService {
    private final byte[] key;
    private final Clock clock;
    public record Claims(int bookingId,int customerId,int roomId,long expiresAt) { }
    public ExpressPassService(byte[] key,Clock clock){
        if(key==null||key.length<32)throw new IllegalArgumentException("Pass signing key must contain at least 32 bytes");
        this.key=key.clone();this.clock=java.util.Objects.requireNonNull(clock);
    }
    public static ExpressPassService configured(){
        String raw = quanlykhachsan.backend.utils.SecretConfiguration.required("HMAC_IDENTITY_KEY");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            bytes = java.util.Arrays.copyOf(raw.getBytes(StandardCharsets.UTF_8), 32);
        }
        if (bytes == null || bytes.length < 32) {
            bytes = java.util.Arrays.copyOf(bytes != null ? bytes : raw.getBytes(StandardCharsets.UTF_8), 32);
        }
        return new ExpressPassService(bytes, Clock.systemUTC());
    }
    public String issue(int booking,int customer,int room){
        if(booking<1||customer<1||room<1)throw new IllegalArgumentException("Invalid pass references");
        String payload="AGP1|"+booking+"|"+customer+"|"+room+"|"+(clock.instant().getEpochSecond()+900);
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
    }
    public Claims verify(String token){
        try{
            if(token==null||token.length()>512)throw new IllegalArgumentException();
            String[] parts=token.split("\\.",-1);
            if(parts.length!=2||!java.security.MessageDigest.isEqual(sign(parts[0]),Base64.getUrlDecoder().decode(parts[1])))throw new IllegalArgumentException();
            String[] claims=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8).split("\\|",-1);
            if(claims.length!=5||!claims[0].equals("AGP1"))throw new IllegalArgumentException();
            var result=new Claims(Integer.parseInt(claims[1]),Integer.parseInt(claims[2]),Integer.parseInt(claims[3]),Long.parseLong(claims[4]));
            long now=clock.instant().getEpochSecond();
            if(result.bookingId()<1||result.customerId()<1||result.roomId()<1||result.expiresAt()<=now||result.expiresAt()>now+900)throw new IllegalArgumentException();
            return result;
        }catch(RuntimeException ex){throw new IllegalArgumentException("Invalid or expired check-in pass");}
    }
    private byte[] sign(String payload){
        try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return mac.doFinal(("express-checkin-pass|"+payload).getBytes(StandardCharsets.UTF_8));
        }catch(GeneralSecurityException ex){throw new IllegalStateException("Pass signing unavailable",ex);}
    }
    public String svg(String token){
        verify(token);
        var image=new BarcodeQRCode(token,1,1,null).createAwtImage(Color.BLACK,Color.WHITE);
        int width=image.getWidth(null),height=image.getHeight(null);
        var pixels=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        var graphics=pixels.createGraphics();try{graphics.drawImage(image,0,0,null);}finally{graphics.dispose();}
        var path=new StringBuilder();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((pixels.getRGB(x,y)&0xffffff)==0)path.append("M").append(x+4).append(' ').append(y+4).append("h1v1h-1z");
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "+(width+8)+" "+(height+8)+"\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><path fill=\"black\" d=\""+path+"\"/></svg>";
    }
}
