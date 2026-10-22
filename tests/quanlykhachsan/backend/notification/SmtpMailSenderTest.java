package quanlykhachsan.backend.notification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmtpMailSenderTest {
    @Test void protectsHeadersAndPreservesStableMessageIdentity(){
        assertFalse(SmtpMailSender.mailbox("a@example.test\r\nBcc: x@example.test"));
        assertTrue(SmtpMailSender.mailbox("dev+test@example.test"));
        var mail=new NotificationWorker.Mail(123,"dev@example.test","Xác nhận\r\nInjection","Xin chào");
        String first=SmtpMailSender.message("sender@example.test",mail);assertEquals(first,SmtpMailSender.message("sender@example.test",mail));
        assertTrue(first.contains("Message-ID: <aurelia-notification-123@example.test>"));assertFalse(first.contains("\r\nInjection"));assertFalse(first.contains("Xin chào"));
    }
    @Test void retriesAreBoundedAndUnconfiguredSmtpDoesNotPretendSuccess(){
        assertEquals(30,NotificationWorker.retrySeconds(1));assertEquals(3600,NotificationWorker.retrySeconds(99));
        var result=new SmtpMailSender(java.util.Map.of()).send(new NotificationWorker.Mail(1,"a@example.test","Subject","Body"));assertEquals("RETRY",result.state());
    }
}
