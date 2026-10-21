package quanlykhachsan.backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoogleOtpMailEncodingTest {
    @Test void vietnameseSubjectAndBodySurviveAsciiSmtpTransport() {
        String message = GoogleOtpAuthService.otpMessage("sender@example.test", "guest@example.test", "1234");
        assertTrue(message.chars().allMatch(value -> value < 128), "Wire data must be safe for the SMTP ASCII writer");
        assertTrue(message.contains("Content-Type: text/plain; charset=UTF-8\r\n"));
        assertTrue(message.contains("Content-Transfer-Encoding: base64\r\n"));
        var subject = Pattern.compile("Subject: =\\?UTF-8\\?B\\?([^?]+)\\?=").matcher(message);
        assertTrue(subject.find());
        assertEquals("Mã xác thực Aurelia Grand Palace", new String(Base64.getDecoder().decode(subject.group(1)), StandardCharsets.UTF_8));
        String encodedBody = message.split("\r\n\r\n", 2)[1];
        String body = new String(Base64.getMimeDecoder().decode(encodedBody), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("Mã OTP đăng nhập Aurelia của bạn là: 1234\r\n\r\n"));
        assertTrue(body.contains("Mã có hiệu lực trong 5 phút và chỉ được dùng một lần."));
        assertTrue(body.contains("Nếu bạn không yêu cầu đăng nhập, hãy bỏ qua email này."));
        assertFalse(body.contains("?"));
        for (String line : encodedBody.split("\r\n")) assertTrue(line.length() <= 76);
    }
}
