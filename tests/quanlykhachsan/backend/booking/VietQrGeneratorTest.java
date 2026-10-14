package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.Crc16Ccitt;
import static org.junit.jupiter.api.Assertions.*;

class VietQrGeneratorTest {
    @Test void payloadTagsChecksumAndLocalPngAreValid()throws Exception {
        assertEquals("29B1",Crc16Ccitt.checksum("123456789"));
        var qr=new VietQrGenerator().generate("970422","0901234567899",new BigDecimal("1500000.00"),"FOLIO-MASTER-101");
        assertTrue(qr.qr_code_string().startsWith("00020101021238"));
        String payload=qr.qr_code_string();assertEquals(Crc16Ccitt.checksum(payload.substring(0,payload.length()-4)),payload.substring(payload.length()-4));
        var fields=parse(payload);assertEquals("704",fields.get("53"));assertEquals("1500000",fields.get("54"));assertEquals("VN",fields.get("58"));
        var merchant=parse(fields.get("38"));assertEquals("A000000727",merchant.get("00"));assertEquals("970422",parse(merchant.get("01")).get("00"));
        assertEquals("FOLIO-MASTER-101",parse(fields.get("62")).get("08"));
        var image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(qr.qr_image_base64())));assertNotNull(image);assertEquals(300,image.getWidth());assertEquals(300,image.getHeight());
        assertThrows(IllegalArgumentException.class,()->new VietQrGenerator().generate("970422","0901234567899",new BigDecimal("1.50"),"FOLIO-MASTER-101"));
    }
    private static Map<String,String> parse(String text){Map<String,String> fields=new LinkedHashMap<>();int index=0;while(index<text.length()){String tag=text.substring(index,index+2);int n=Integer.parseInt(text.substring(index+2,index+4));fields.put(tag,text.substring(index+4,index+4+n));index+=4+n;}assertEquals(text.length(),index);return fields;}
}
