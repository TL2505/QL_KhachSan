package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.itextpdf.text.pdf.BarcodeQRCode;
import quanlykhachsan.backend.utils.Crc16Ccitt;

/** Local EMVCo payload and PNG generation using the already bundled Java QR encoder. */
public final class VietQrGenerator {
    public Qr generate(String bin,String account,BigDecimal amount,String reference)throws IOException {
        if(bin==null || !bin.matches("[0-9]{6}") || account==null || !account.matches("[0-9]{6,19}"))throw new IllegalArgumentException("Invalid beneficiary configuration");
        if(reference==null || !reference.matches("[A-Za-z0-9-]{1,25}"))throw new IllegalArgumentException("Invalid payment reference");
        if(amount==null || amount.signum()<=0 || amount.stripTrailingZeros().scale()>0 || amount.compareTo(new BigDecimal("999999999999"))>0)throw new IllegalArgumentException("VND amount must be positive whole dong");
        String bank=tlv("00",bin)+tlv("01",account);
        String merchant=tlv("00","A000000727")+tlv("01",bank)+tlv("02","QRIBFTTA");
        String payload=tlv("00","01")+tlv("01","12")+tlv("38",merchant)+tlv("53","704")
            +tlv("54",amount.stripTrailingZeros().toPlainString())+tlv("58","VN")+tlv("62",tlv("08",reference))+"6304";
        payload+=Crc16Ccitt.checksum(payload);
        Image qr=new BarcodeQRCode(payload,300,300,null).createAwtImage(Color.BLACK,Color.WHITE);
        BufferedImage png=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=png.createGraphics();try{graphics.drawImage(qr,0,0,300,300,null);}finally{graphics.dispose();}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(!ImageIO.write(png,"png",bytes))throw new IOException("PNG writer unavailable");
        return new Qr(payload,Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }
    private static String tlv(String tag,String value) {
        int length=value.getBytes(StandardCharsets.UTF_8).length;
        if(length>99)throw new IllegalArgumentException("QR field exceeds EMVCo length");
        return tag+String.format(java.util.Locale.ROOT,"%02d",length)+value;
    }
    public record Qr(String qr_code_string,String qr_image_base64) { }
}
