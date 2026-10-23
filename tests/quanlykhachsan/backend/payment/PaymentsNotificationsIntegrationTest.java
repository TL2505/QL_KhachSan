package quanlykhachsan.backend.payment;

import com.google.gson.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.backend.booking.*;
import quanlykhachsan.backend.notification.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;

/** Runs only against an explicitly provisioned disposable MariaDB schema. No external calls. */
class PaymentsNotificationsIntegrationTest {
    @BeforeAll static void schema()throws Exception {Phase2TestDatabase.require();}
    @Test void partialThenFullPaymentConfirmsOnceAndReplaysAfterCheckout()throws Exception {
        var f=fixture();var service=new PaymentReconciliationService();var one=payment(f,"part-one",400);
        assertTrue(service.settle(one).contains("PARTIAL"));assertEquals("pending",text("SELECT status FROM bookings WHERE id="+f.booking));
        assertEquals("PARTIAL",projection(f));service.settle(payment(f,"part-two",600));assertEquals("confirmed",text("SELECT status FROM bookings WHERE id="+f.booking));assertEquals("PAID",projection(f));
        assertEquals(1,count("customer_notifications WHERE booking_id="+f.booking+" AND notification_type='booking_confirmed'"));
        assertEquals(3,count("notification_outbox WHERE booking_id="+f.booking));
        execute("UPDATE bookings SET status='checked_out' WHERE id="+f.booking);
        assertEquals(service.settle(one),service.settle(one));assertEquals(2,count("folio_transactions WHERE folio_id="+f.folio+" AND charge_type='payment'"));
    }
    @Test void simultaneousDuplicateAndConflictingDeliveriesCannotDoubleCredit()throws Exception {
        var f=fixture();var p=payment(f,"duplicate",1000);var s=new PaymentReconciliationService();var executor=Executors.newFixedThreadPool(2);
        try{var a=executor.submit(()->s.settle(p));var b=executor.submit(()->s.settle(p));assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
        assertTrue(s.settle(payment(f,"duplicate",999)).contains("TRANSACTION_CONFLICT"));assertEquals(1,count("folio_transactions WHERE folio_id="+f.folio+" AND charge_type='payment'"));assertEquals(1,count("notification_outbox WHERE booking_id="+f.booking+" AND event_type='payment_paid'"));
    }
    @Test void beneficiaryReferenceProviderAndTimeAreCheckedAndRejectedTransfersRemainAuditable()throws Exception {
        var s=new PaymentReconciliationService();
        for(String reason:List.of("BENEFICIARY_MISMATCH","REFERENCE_MISMATCH","LINK_MISMATCH","PROVIDER_NOT_CONFIRMED","OUTSIDE_PAYMENT_WINDOW","LATE_PAYMENT","CURRENCY_MISMATCH")){
            var f=fixture();var p=payment(f,reason,1000);if(reason.equals("LATE_PAYMENT"))execute("UPDATE bookings SET status='cancelled' WHERE id="+f.booking);
            var altered=new PaymentReconciliationService.VerifiedPayment(p.provider(),p.transactionId(),p.orderCode(),p.amount(),reason.equals("CURRENCY_MISMATCH")?"USD":"VND",reason.equals("BENEFICIARY_MISMATCH")?"99999999":p.account(),reason.equals("REFERENCE_MISMATCH")?"WRONG":p.reference(),reason.equals("LINK_MISMATCH")?"WRONG":p.linkId(),reason.equals("OUTSIDE_PAYMENT_WINDOW")?p.occurredAt().plusHours(1):p.occurredAt(),reason.equals("PROVIDER_NOT_CONFIRMED")?"99":"00");
            assertTrue(s.settle(altered).contains(reason),reason);assertEquals(0,count("folio_transactions WHERE folio_id="+f.folio+" AND transaction_type='credit'"));assertEquals("RECONCILIATION_ERROR",projection(f));
            if(reason.equals("REFERENCE_MISMATCH")){s.settle(payment(f,"corrected-new-transaction",100));assertEquals("PARTIAL",projection(f));}
        }
        var f=fixture();var p=payment(f,"unknown",1000);assertTrue(s.settle(new PaymentReconciliationService.VerifiedPayment("payos",p.transactionId(),99999999,p.amount(),"VND",p.account(),p.reference(),p.linkId(),p.occurredAt(),"00")).contains("UNKNOWN_REFERENCE"));
    }
    @Test void transactionFailureRollsBackLedgerBalanceBookingAndBothNotificationChannels()throws Exception {
        var f=fixture();execute("CREATE TRIGGER payment_test_failure BEFORE INSERT ON payment_reconciliations FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
        try{assertThrows(SQLException.class,()->new PaymentReconciliationService().settle(payment(f,"rollback",1000)));}finally{execute("DROP TRIGGER payment_test_failure");}
        assertEquals("pending",text("SELECT status FROM bookings WHERE id="+f.booking));assertEquals(0,count("folio_transactions WHERE folio_id="+f.folio+" AND transaction_type='credit'"));assertEquals(0,count("notification_outbox WHERE booking_id="+f.booking));assertEquals(0,count("customer_notifications WHERE booking_id="+f.booking));assertEquals("WAITING",projection(f));
    }
    @Test void overpaymentKeepsExcessSeparateFromRevenue()throws Exception {
        var f=fixture();new PaymentReconciliationService().settle(payment(f,"over",1500));assertEquals("0.00",text("SELECT balance FROM folios WHERE id="+f.folio));assertEquals("500.00",text("SELECT excess_amount FROM payment_reconciliations WHERE folio_id="+f.folio));assertEquals(1,count("folio_transactions WHERE folio_id="+f.folio+" AND charge_type='credit_adjustment'"));
    }
    @Test void cashAndPosUseTheSameAuditAndDurableEvents()throws Exception {
        for(String method:List.of("cash","POS")){var f=fixture();String key=UUID.randomUUID().toString();try(var c=DatabaseConnection.getConnection()){
            var request=new FolioDao.PaymentRequest(f.folio,key,"synthetic",new BigDecimal("1000.00"),method,false,ZoneId.of("Asia/Ho_Chi_Minh"));var service=new FolioService();assertEquals(200,service.pay(c,request).statusCode());assertEquals(200,service.pay(c,request).statusCode());
        }assertEquals(1,count("payment_reconciliations WHERE folio_id="+f.folio));assertEquals(2,count("notification_outbox WHERE booking_id="+f.booking));assertEquals("PAID",projection(f));}
    }
    @Test void outboxSurvivesRestartRetriesKnownFailureAndNeverResendsAmbiguousOutcome()throws Exception {
        // Other tests queue mail too; only activate this test's messages for this worker.
        execute("UPDATE notification_outbox SET state='SKIPPED' WHERE state IN ('PENDING','RETRY')");var f=fixture();enqueue(f,"test-retry");AtomicInteger sends=new AtomicInteger();
        try(var first=worker(mail->{sends.incrementAndGet();return new NotificationWorker.Outcome("RETRY","SMTP_451");})){assertTrue(first.deliverOne());}
        assertEquals("RETRY",text("SELECT state FROM notification_outbox WHERE event_key='test-retry'"));execute("UPDATE notification_outbox SET next_attempt_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND) WHERE event_key='test-retry'");
        try(var restarted=worker(mail->{sends.incrementAndGet();return new NotificationWorker.Outcome("SENT",null);})){assertTrue(restarted.deliverOne());assertFalse(restarted.deliverOne());}
        assertEquals(2,sends.get());assertEquals(2,count("notification_delivery_attempts a JOIN notification_outbox o ON o.id=a.outbox_id WHERE o.event_key='test-retry'"));
        enqueue(f,"test-unknown");try(var worker=worker(mail->{sends.incrementAndGet();return new NotificationWorker.Outcome("UNKNOWN","SMTP_ACCEPTANCE_UNKNOWN");})){assertTrue(worker.deliverOne());assertFalse(worker.deliverOne());}assertEquals("UNKNOWN",text("SELECT state FROM notification_outbox WHERE event_key='test-unknown'"));
    }
    @Test void queueClaimAndEventDedupeWorkAcrossWorkers()throws Exception {
        execute("UPDATE notification_outbox SET state='SKIPPED' WHERE state IN ('PENDING','RETRY')");var f=fixture();enqueue(f,"concurrent-event");enqueue(f,"concurrent-event");assertEquals(1,count("customer_notifications WHERE event_key='concurrent-event'"));AtomicInteger sends=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(2);try(var a=worker(mail->{sends.incrementAndGet();return new NotificationWorker.Outcome("SENT",null);});var b=worker(mail->{sends.incrementAndGet();return new NotificationWorker.Outcome("SENT",null);})){
            var first=pool.submit(a::deliverOne);var second=pool.submit(b::deliverOne);first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);assertEquals(1,sends.get());
        }finally{pool.shutdownNow();}
    }
    @Test void remindersAreDeduplicatedAndExpiredSendingLeaseRequiresReview()throws Exception {
        var f=fixture();execute("UPDATE bookings SET status='confirmed',check_in_date=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 3 HOUR),check_out_date=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 2 DAY) WHERE id="+f.booking);
        try(var w=worker(mail->new NotificationWorker.Outcome("SENT",null))){w.reminders();w.reminders();assertEquals(1,count("notification_outbox WHERE booking_id="+f.booking+" AND event_type='checkin_reminder'"));}
        enqueue(f,"lease-test");execute("UPDATE notification_outbox SET state='SENDING',attempts=1,lease_until=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 MINUTE) WHERE event_key='lease-test'");execute("INSERT INTO notification_delivery_attempts(outbox_id,attempt_number,state) SELECT id,1,'SENDING' FROM notification_outbox WHERE event_key='lease-test'");
        try(var w=worker(mail->new NotificationWorker.Outcome("SENT",null))){w.recoverExpiredLeases();}assertEquals("UNKNOWN",text("SELECT state FROM notification_outbox WHERE event_key='lease-test'"));
    }
    @Test void webStayLifecycleBindsLocalDateTimeAndQueuesNinetyPercentCheckoutReminder()throws Exception {
        var f=fixture();ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime checkIn=LocalDateTime.now(zone).plusHours(2).withSecond(0).withNano(0),checkOut=checkIn.plusDays(1);
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("UPDATE bookings SET status='confirmed',check_in_date=?,check_out_date=?,check_in_actual=NULL,check_out_actual=NULL WHERE id=?")){
            q.setObject(1,checkIn);q.setObject(2,checkOut);q.setInt(3,f.booking);assertEquals(1,q.executeUpdate());
        }
        Clock afterCheckIn=Clock.fixed(checkIn.plusMinutes(5).atZone(zone).toInstant(),zone);
        assertEquals(1,new WebBookingLifecycleEngine(DatabaseConnection::getConnection,afterCheckIn).processDue());
        assertEquals("checked_in",text("SELECT status FROM bookings WHERE id="+f.booking));
        assertEquals(checkIn.plusMinutes(5).format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")),text("SELECT DATE_FORMAT(check_in_actual,'%Y-%m-%d %H:%i:%s') FROM bookings WHERE id="+f.booking));

        Clock checkoutReminder=Clock.fixed(checkOut.minusHours(1).atZone(zone).toInstant(),zone);
        new WebBookingLifecycleEngine(DatabaseConnection::getConnection,checkoutReminder).processDue();
        assertEquals(1,count("notification_outbox WHERE booking_id="+f.booking+" AND event_type='checkout_reminder'"));

        Clock afterCheckOut=Clock.fixed(checkOut.plusMinutes(1).atZone(zone).toInstant(),zone);
        assertEquals(1,new WebBookingLifecycleEngine(DatabaseConnection::getConnection,afterCheckOut).processDue());
        assertEquals("checked_out",text("SELECT status FROM bookings WHERE id="+f.booking));
        assertEquals("vacant_dirty",text("SELECT r.status FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.id="+f.booking));
        assertEquals(1,count("cleaning_tasks WHERE booking_id="+f.booking+" AND status='pending'"));
    }
    @Test void unpaidWebCounterStayWaitsForStaffCollectionAndNotifiesAfterThirtyMinutes()throws Exception {
        var f=fixture();ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");LocalDateTime databaseNow;
        try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT CURRENT_TIMESTAMP(6)")){
            r.next();databaseNow=r.getTimestamp(1).toLocalDateTime();
        }
        LocalDateTime checkIn=databaseNow.minusDays(1),checkOut=databaseNow.minusMinutes(40);
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement(
                "UPDATE bookings SET status='checked_in',customer_type='Web',payment_choice='counter',check_in_date=?,check_out_date=?,check_in_actual=?,check_out_actual=NULL WHERE id=?")){
            q.setObject(1,checkIn);q.setObject(2,checkOut);q.setObject(3,checkIn);q.setInt(4,f.booking);assertEquals(1,q.executeUpdate());
        }
        execute("UPDATE rooms SET status='occupied_clean' WHERE id=(SELECT room_id FROM bookings WHERE id="+f.booking+")");

        Clock due=Clock.fixed(databaseNow.atZone(zone).toInstant(),zone);
        assertEquals(1,new WebBookingLifecycleEngine(DatabaseConnection::getConnection,due).processDue());
        // The engine leaves the stay in-house and marks it for staff.
        assertEquals("checked_in",text("SELECT status FROM bookings WHERE id="+f.booking));
        assertNotNull(text("SELECT checkout_overdue_at FROM bookings WHERE id="+f.booking));
        assertEquals("occupied_clean",text("SELECT r.status FROM rooms r JOIN bookings b ON b.room_id=r.id WHERE b.id="+f.booking));
        assertEquals(0,count("cleaning_tasks WHERE booking_id="+f.booking+" AND status='pending'"));
        assertEquals("1050.00",text("SELECT balance FROM folios WHERE id="+f.folio));
        assertEquals(1,count("folio_transactions WHERE folio_id="+f.folio+" AND reference_id='OVERTIME-HOURLY-"+f.booking+"' AND transaction_type='debit'"));
        assertEquals(1,count("staff_notifications WHERE booking_id="+f.booking+" AND event_key LIKE 'checkout-overdue:%'"));
        assertEquals(0,new WebBookingLifecycleEngine(DatabaseConnection::getConnection,due).processDue());
        assertEquals("1050.00",text("SELECT balance FROM folios WHERE id="+f.folio));

        String key=UUID.randomUUID().toString();
        var payment=new FolioDao.PaymentRequest(f.folio,key,"counter-checkout-"+key,new BigDecimal("1050.00"),"cash",true,zone,1);
        try(Connection c=DatabaseConnection.getConnection()) { assertEquals(200,new FolioService().pay(c,payment).statusCode()); }
        assertEquals("checked_out",text("SELECT status FROM bookings WHERE id="+f.booking));
        assertEquals("vacant_dirty",text("SELECT r.status FROM rooms r JOIN bookings b ON b.room_id=r.id WHERE b.id="+f.booking));
        assertEquals(1,count("cleaning_tasks WHERE booking_id="+f.booking+" AND status='pending'"));
        assertEquals(1,count("staff_collections WHERE folio_id="+f.folio+" AND reference_id='"+key+"'"));
    }
    @Test void anotherGatewayUsesTheSameLinksSettlementAndNotificationTables()throws Exception {
        var f=fixture();AtomicInteger creates=new AtomicInteger();
        PaymentGateway gateway=new PaymentGateway(){
            public String provider(){return "future_gateway";}
            public Beneficiary beneficiary(){return new Beneficiary("12345678","SYNTHETIC","970422");}
            public void requireConfigured(){ }
            public JsonObject createLink(long order,long amount,String ref,long expires){creates.incrementAndGet();JsonObject j=new JsonObject();j.addProperty("paymentLinkId","future-"+order);j.addProperty("qrCode","synthetic-qr-"+order);j.addProperty("checkoutUrl","https://example.test/checkout/"+order);return j;}
        };
        JsonObject link;try(var c=DatabaseConnection.getConnection()){link=new PaymentLinkService(gateway).link(c,f.folio);assertEquals(link.get("order_code"),new PaymentLinkService(gateway).link(c,f.folio).get("order_code"));}assertEquals(1,creates.get());
        long order=link.get("order_code").getAsLong();var p=new PaymentReconciliationService.VerifiedPayment("future_gateway","future-tx-"+f.booking,order,new BigDecimal("1000"),"VND","12345678",link.get("reference_code").getAsString(),"future-"+order,LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withNano(0),"00");
        assertTrue(new PaymentReconciliationService().settle(p).contains("PAID"));assertEquals("future_gateway",text("SELECT provider FROM payment_reconciliations WHERE folio_id="+f.folio));assertEquals(2,count("notification_outbox WHERE booking_id="+f.booking));
    }
    @Test void legacyBankAdapterAlsoRecordsAppliedAndExcessInTheSharedAudit()throws Exception {
        var f=fixture();String code=text("SELECT code FROM folios WHERE id="+f.folio);var p=new FolioPaymentService.Payment("legacy-"+f.booking,new BigDecimal("1500.00"),code,"synthetic","{}");var service=new FolioPaymentService();assertEquals(service.settle(p),service.settle(p));assertEquals("legacy_bank",text("SELECT provider FROM payment_reconciliations WHERE folio_id="+f.folio));assertEquals("500.00",text("SELECT excess_amount FROM payment_reconciliations WHERE folio_id="+f.folio));assertEquals(2,count("notification_outbox WHERE booking_id="+f.booking));
    }
    @Test void expiredReminderDoesNotSendAndTransientRetriesStopAtFive()throws Exception {
        execute("UPDATE notification_outbox SET state='SKIPPED' WHERE state IN ('PENDING','RETRY')");var f=fixture();execute("UPDATE bookings SET status='confirmed',check_in_date=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 3 HOUR) WHERE id="+f.booking);
        AtomicInteger sends=new AtomicInteger();try(var w=worker(mail->{sends.incrementAndGet();return new NotificationWorker.Outcome("RETRY","SMTP_451");})){
            w.reminders();execute("UPDATE notification_outbox SET state='SKIPPED' WHERE booking_id<>"+f.booking+" AND state IN ('PENDING','RETRY')");execute("UPDATE bookings SET status='cancelled' WHERE id="+f.booking);assertTrue(w.deliverOne());assertEquals(0,sends.get());
            enqueue(f,"retry-limit");for(int i=0;i<5;i++){execute("UPDATE notification_outbox SET next_attempt_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND) WHERE event_key='retry-limit'");assertTrue(w.deliverOne());}assertFalse(w.deliverOne());
        }assertEquals(5,sends.get());assertEquals("FAILED",text("SELECT state FROM notification_outbox WHERE event_key='retry-limit'"));
    }
    @Test void auditHistoryDoesNotExposeAnotherCustomersTransactionsOrDeliveries()throws Exception {
        var f=fixture();var other=fixture();new PaymentReconciliationService().settle(payment(other,"other",1000));enqueue(other,"other-email");int customer=Integer.parseInt(text("SELECT customer_id FROM bookings WHERE id="+f.booking));
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/payment-operations",e->{e.setAttribute("role","customer");e.setAttribute("customer_id",customer);new PaymentOperationsController().handle(e);});
        server.createContext("/api/v1/folios",e->{e.setAttribute("role","guest");e.setAttribute("customer_id",customer);new PaymentHistoryController().handle(e);});server.start();
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort();var http=java.net.http.HttpClient.newHttpClient();
            for(String path:List.of("/api/v1/payment-operations/transactions?folio_id="+other.folio,"/api/v1/payment-operations/notifications","/api/v1/payment-operations/attempts?id="+text("SELECT id FROM notification_outbox WHERE event_key='other-email'"))){var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertEquals(0,JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("data").size());}
            assertEquals(403,http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/folios/"+other.folio+"/payment-history")).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
        }finally{server.stop(0);}
    }
    private static NotificationWorker worker(NotificationWorker.Sender s){return new NotificationWorker(DatabaseConnection::getConnection,s,true,Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));}
    private static void enqueue(Fixture f,String key)throws Exception {try(var c=DatabaseConnection.getConnection()){c.setAutoCommit(false);LifecycleNotifications.booking(c,f.booking,"booking_created",key,"Synthetic","Synthetic event");c.commit();}}
    private static String projection(Fixture f)throws Exception {try(var c=DatabaseConnection.getConnection()){return PaymentStatus.forFolio(c,f.folio,new BigDecimal(text("SELECT balance FROM folios WHERE id="+f.folio))).get("payment_status").getAsString();}}
    private record Fixture(int booking,long folio,long order,String reference,String link) { }
    private static Fixture fixture()throws Exception {
        String u=UUID.randomUUID().toString().replace("-","").substring(0,10);
        try(var c=DatabaseConnection.getConnection()){
            int type=(int)insert(c,"INSERT INTO room_types(name,base_price,hourly_extra_hour_price,capacity) VALUES('Synthetic-"+u+"',1000,50,2)");int room=(int)insert(c,"INSERT INTO rooms(room_number,room_type_id,price,status) VALUES('T-"+u+"',"+type+",1000,'reserved')");int customer=(int)insert(c,"INSERT INTO customers(full_name,identity_card,phone,email) VALUES('Synthetic',NULL,'0000000000','dev@example.test')");
            int booking=(int)insert(c,"INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type) VALUES("+customer+","+room+",DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 2 DAY),1000,'pending','Web')");long folio=insert(c,"INSERT INTO folios(booking_id,folio_type,balance,status,code) VALUES("+booking+",'master',1000,'open','FOLIO-T-"+u+"')");
            execute(c,"INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) SELECT "+folio+",'debit','room_rate',1000,business_date FROM hotel_business_state WHERE id=1");String reference="AH"+u.substring(0,7),link="link-"+u;
            long order=insert(c,"INSERT INTO payment_links(folio_id,amount,reference_code,account_number,account_name,bank_bin,payment_link_id,state,expires_at) VALUES("+folio+",1000,'"+reference+"','12345678','SYNTHETIC','970422','"+link+"','PENDING',DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 15 MINUTE))");return new Fixture(booking,folio,order,reference,link);
        }
    }
    private static PaymentReconciliationService.VerifiedPayment payment(Fixture f,String id,long amount){return new PaymentReconciliationService.VerifiedPayment("payos",id+"-"+f.booking,f.order,BigDecimal.valueOf(amount),"VND","12345678",f.reference,f.link,LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withNano(0),"00");}
    private static long insert(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.executeUpdate(sql,Statement.RETURN_GENERATED_KEYS);try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}}
    private static void execute(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
    private static void execute(String sql)throws SQLException{try(var c=DatabaseConnection.getConnection()){execute(c,sql);}}
    private static String text(String sql)throws SQLException{try(var c=DatabaseConnection.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private static long count(String tail)throws SQLException{return Long.parseLong(text("SELECT COUNT(*) FROM "+tail));}
}
