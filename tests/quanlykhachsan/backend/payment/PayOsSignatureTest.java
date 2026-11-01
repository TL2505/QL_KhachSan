package quanlykhachsan.backend.payment;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PayOsSignatureTest {
    @Test void matchesPublishedPayOsWebhookVectorAndRejectsTampering(){
        // Public example, never a real merchant checksum key.
        String key="1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
        JsonObject data=JsonParser.parseString("""
            {"orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678","reference":"TF230204212323","transactionDateTime":"2023-02-04 18:25:00","currency":"VND","paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00","desc":"Thành công","counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"","counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""}
            """).getAsJsonObject();
        String signature="412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";
        assertTrue(PayOsSignature.verify(data,signature,key));data.addProperty("amount",3001);assertFalse(PayOsSignature.verify(data,signature,key));assertFalse(PayOsSignature.verify(data,"invalid",key));
    }
    @Test void canonicalizesNullsAndNestedKeysWithoutUrlEncoding(){
        JsonObject j=JsonParser.parseString("{\"z\":null,\"a\":\"a & b\",\"items\":[{\"z\":1,\"a\":2}],\"u\":\"undefined\"}").getAsJsonObject();
        assertEquals("a=a & b&items=[{\"a\":2,\"z\":1}]&u=&z=",PayOsSignature.canonical(j));
    }
    @Test void missingCredentialsAndNonHttpsRedirectsFailClosed(){
        assertThrows(IllegalStateException.class,()->PayOsConfig.from(java.util.Map.of()).requireLink());
        assertThrows(IllegalStateException.class,()->new PayOsConfig("id","api","checksum","12345678","SYNTHETIC","970422","http://localhost/a","https://example.test/b").requireLink());
    }
}
