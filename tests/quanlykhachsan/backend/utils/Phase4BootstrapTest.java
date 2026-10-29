package quanlykhachsan.backend.utils;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.frontend.api.ApiConfig;

/** Reproduces the container's schema-only base, prerequisites and exact manifest on MariaDB. */
class Phase4BootstrapTest {
    @Test void manifestIncludesEveryMigrationExactlyOnce()throws Exception {
        var listed=Files.readAllLines(Path.of("database/bootstrap/migrations.txt")).stream().filter(s->!s.isBlank()).toList();
        try(var files=Files.list(Path.of("database/migrations"))){
            var actual=files.filter(p->p.toString().endsWith(".sql")).map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
            assertEquals(actual,new HashSet<>(listed));assertEquals(actual.size(),listed.size());
        }
        String init=Files.readString(Path.of("database/bootstrap/00-init.sh"));
        assertTrue(init.contains("set -euo pipefail"));assertTrue(init.contains("-- SEED DATA FROM PRODUCTION"));
        assertTrue(Files.readString(Path.of("src/quanlykhachsan/backend/utils/UpdateDbUtility.java")).contains("v4_7_web_booking_lifecycle.sql"),
                "The local backend updater must apply the web-booking lifecycle migration too");
        for(String compose:List.of("docker-compose.yml","docker-compose.prod.yml"))assertTrue(Files.readString(Path.of(compose)).contains("./database/bootstrap/00-init.sh:/docker-entrypoint-initdb.d/00-init.sh:ro"));
    }
    @Test void freshConfiguredMariaDbAppliesBaseAndAllMigrationsWithoutSeeds()throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("hotel.phase4.verifyDatabase"),"Run with -Dhotel.phase4.verifyDatabase=true to create and drop an isolated schema on the configured MariaDB");
        String schema="phase4_"+UUID.randomUUID().toString().replace("-","")+"_test";
        try(var c=DriverManager.getConnection(ApiConfig.getJdbcUrl(),ApiConfig.getDbUser(),ApiConfig.getDbPassword());var admin=c.createStatement()){
            String originalCatalog=c.getCatalog();
            try(var r=admin.executeQuery("SELECT VERSION()")){assertTrue(r.next());System.out.println("Phase 4 database engine: "+r.getString(1));assertTrue(r.getString(1).startsWith("11.")&&r.getString(1).contains("MariaDB"),"MariaDB 11 required");}
            admin.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                c.setCatalog(schema);
                String base=Files.readString(Path.of("database/hotel.sql"));
                execute(c,base.substring(0,base.indexOf("-- SEED DATA FROM PRODUCTION")).replace("hotel_prod_db",schema));
                execute(c,Files.readString(Path.of("database/bootstrap/prerequisites.sql")));
                for(String migration:Files.readAllLines(Path.of("database/bootstrap/migrations.txt"))){
                    if(migration.isBlank())continue;
                    System.out.println("Bootstrap migration: "+migration);
                    execute(c,Files.readString(Path.of("database/migrations",migration)).replace("hotel_prod_db",schema));
                }
                for(String table:List.of("guest_identity_vault","service_orders","customer_notifications","ai_chat_logs","staff_collections","pricing_rule_audit","bank_webhook_logs")){
                    try(var r=c.getMetaData().getTables(schema,null,table,new String[]{"TABLE"})){assertTrue(r.next(),table);}
                }
                try(var q=c.createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM users")){r.next();assertEquals(0,r.getInt(1),"Production seed must not be imported");}
                verifyTransactionalWorkflows(c,schema);
                System.out.println("Fresh base + all manifest migrations succeeded: "+schema);
            } finally {
                // Only this generated, verified test name may be removed.
                assertTrue(schema.matches("phase4_[0-9a-f]{32}_test"));
                c.setCatalog(originalCatalog);
                admin.execute("DROP DATABASE `"+schema+"`");
            }
        }
    }
    private void verifyTransactionalWorkflows(Connection c,String schema)throws Exception {
        try(var s=c.createStatement()){
            s.execute("INSERT INTO room_types(id,name,base_price,capacity) VALUES(1,'Synthetic Suite',100,2)");
            s.execute("INSERT INTO rooms(id,room_number,room_type_id,price,status) VALUES(1,'TEST-1',1,100,'occupied_clean'),(2,'TEST-2',1,100,'reserved')");
            s.execute("INSERT INTO customers(id,full_name,phone) VALUES(1,'Synthetic Guest','TEST-1'),(2,'Synthetic Other','TEST-2')");
            s.execute("INSERT INTO bookings(id,customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type) VALUES(1,1,1,CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 2 DAY),100,'checked_in','Web'),(2,2,2,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 2 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 3 DAY),10,'pending','Web')");
            s.execute("INSERT INTO services(id,name,price,status,category) VALUES(1,'Synthetic Meal',12.35,'active','FNB'),(2,'Unavailable',1,'inactive','SPA')");
            s.execute("INSERT INTO folios(id,booking_id,folio_type,balance,status,code) VALUES(1,1,'guest',0,'open','TEST-FOLIO-1'),(2,2,'master',10,'open','TEST-FOLIO-2')");
            s.execute("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) VALUES(2,'debit','room_rate',10,CURRENT_DATE)");
        }
        var dao=new quanlykhachsan.backend.hotelservice.ServiceOrderDAO(()->{
            var connection=DriverManager.getConnection(ApiConfig.getJdbcUrl(),ApiConfig.getDbUser(),ApiConfig.getDbPassword());connection.setCatalog(schema);return connection;
        });
        String key=UUID.randomUUID().toString();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(6);
        var delivered=new java.util.concurrent.CountDownLatch(1);
        quanlykhachsan.frontend.utils.SessionManagerUtil.setAccessToken("synthetic-test-only");
        var alerts=new quanlykhachsan.frontend.worker.ServiceOrderAlertWorker((count,orders)->{
            if(count>0){assertTrue(javax.swing.SwingUtilities.isEventDispatchThread());delivered.countDown();}
        },()->{
            var envelope=new com.google.gson.JsonObject();envelope.addProperty("status","success");envelope.add("data",JsonUtil.getGson().toJsonTree(dao.getActiveOrders()));return envelope.toString();
        });
        long deliveryStarted=System.nanoTime();alerts.start();
        try{
            var futures=new ArrayList<java.util.concurrent.Future<Long>>();
            for(int i=0;i<6;i++)futures.add(workers.submit(()->dao.createOrderWithFolioDebit(order(),key,1).getId()));
            long id=futures.get(0).get(20,java.util.concurrent.TimeUnit.SECONDS);
            for(var future:futures)assertEquals(id,future.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(delivered.await(3,java.util.concurrent.TimeUnit.SECONDS),"Database-to-desktop delivery exceeded three seconds");
            System.out.printf(java.util.Locale.ROOT,"MariaDB create-to-desktop polling latency (DAO source, HTTP excluded): %.3f ms%n",(System.nanoTime()-deliveryStarted)/1_000_000.0);
        }finally{alerts.stop();quanlykhachsan.frontend.utils.SessionManagerUtil.clear();workers.shutdownNow();}
        assertEquals(new java.math.BigDecimal("24.70"),scalar(c,"SELECT balance FROM folios WHERE id=1"));
        var checkout=new quanlykhachsan.backend.booking.FolioDao.PaymentRequest(1,UUID.randomUUID().toString(),"synthetic-unpaid-checkout",new java.math.BigDecimal("0.00"),"cash",true,java.time.ZoneOffset.UTC,1);
        assertEquals(409,new quanlykhachsan.backend.booking.FolioService().pay(c,checkout).statusCode());
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id=1 AND status='checked_in'").intValueExact());
        var failedPayment=new quanlykhachsan.backend.booking.FolioDao.PaymentRequest(1,UUID.randomUUID().toString(),"synthetic-payment-rollback",new java.math.BigDecimal("1.00"),"POS",false,java.time.ZoneOffset.UTC,1);
        assertThrows(SQLException.class,()->new quanlykhachsan.backend.booking.FolioService().pay(c,failedPayment,()->{throw new SQLException("Synthetic collection failure");}));
        assertEquals(new java.math.BigDecimal("24.70"),scalar(c,"SELECT balance FROM folios WHERE id=1"));
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM service_orders").intValueExact());
        var changed=order();changed.setQuantity(3);
        assertThrows(IllegalStateException.class,()->dao.createOrderWithFolioDebit(changed,key,1));
        assertThrows(SecurityException.class,()->dao.createOrderWithFolioDebit(order(),UUID.randomUUID().toString(),2));
        var inactive=order();inactive.setServiceId(2);inactive.setCategory("SPA");
        assertThrows(IllegalArgumentException.class,()->dao.createOrderWithFolioDebit(inactive,UUID.randomUUID().toString(),1));
        try(var s=c.createStatement()){s.execute("CREATE TRIGGER phase4_reject_post BEFORE INSERT ON folio_transactions FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic ledger failure'");}
        try{assertThrows(SQLException.class,()->dao.createOrderWithFolioDebit(order(),UUID.randomUUID().toString(),1));}
        finally{try(var s=c.createStatement()){s.execute("DROP TRIGGER phase4_reject_post");}}
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM service_orders").intValueExact());
        assertEquals(new java.math.BigDecimal("24.70"),scalar(c,"SELECT balance FROM folios WHERE id=1"));
        var payment=new quanlykhachsan.backend.booking.FolioDao.PaymentRequest(2,UUID.randomUUID().toString(),"synthetic-cash",new java.math.BigDecimal("10.00"),"cash",false,java.time.ZoneOffset.UTC,1);
        var service=new quanlykhachsan.backend.booking.FolioService();
        var paid=service.pay(c,payment);assertEquals(200,paid.statusCode());assertEquals(paid.body(),service.pay(c,payment).body());
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM staff_collections").intValueExact());
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id=2 AND status='confirmed'").intValueExact());
        assertTrue(new quanlykhachsan.backend.report.CollectionReconciliation().read(c,java.time.LocalDate.now()).reconciled());
        try(var s=c.createStatement()){s.execute("UPDATE staff_collections SET amount=9.99 WHERE folio_id=2");}
        assertFalse(new quanlykhachsan.backend.report.CollectionReconciliation().read(c,java.time.LocalDate.now()).reconciled());
        var approval=new quanlykhachsan.backend.report.RateApprovalService();
        var today=java.time.LocalDate.now();
        var rate=new quanlykhachsan.backend.report.RateApprovalService.Rate(1,today.toString(),new java.math.BigDecimal("1.10"),0,null);
        assertThrows(SecurityException.class,()->approval.apply(c,List.of(rate),1,false,today));
        approval.apply(c,List.of(rate),1,true,today);
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM pricing_rule_audit WHERE actor_id=1 AND rate_date=CURRENT_DATE").intValueExact());
        assertThrows(IllegalStateException.class,()->approval.apply(c,List.of(rate),1,true,today));
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM pricing_rule_audit").intValueExact());
        long orderId=scalar(c,"SELECT id FROM service_orders LIMIT 1").longValueExact();
        assertTrue(dao.updateOrderStatus(orderId,"CANCELLED"));assertTrue(dao.updateOrderStatus(orderId,"CANCELLED"));
        assertEquals(0,scalar(c,"SELECT balance FROM folios WHERE id=1").signum());
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM folio_transactions WHERE reference_id LIKE 'CANCEL-SERVICE-ORDER-%'").intValueExact());
        assertThrows(IllegalStateException.class,()->dao.updateOrderStatus(orderId,"ACCEPTED"));
        try(var s=c.createStatement()){
            s.execute("INSERT INTO bookings(id,customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type,payment_choice,created_at) VALUES(3,1,1,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 5 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 6 DAY),5,'pending','Web','qr',DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 70 MINUTE))");
            s.execute("INSERT INTO folios(id,booking_id,folio_type,balance,status,code) VALUES(3,3,'master',5,'open','TEST-EXPIRED')");
            s.execute("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) VALUES(3,'debit','room_rate',5,CURRENT_DATE)");
            s.execute("INSERT INTO bookings(id,customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type,payment_choice,created_at) VALUES(30,1,1,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 5 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 6 DAY),5,'pending','Walk-in','counter',DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 70 MINUTE)),(31,1,1,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 5 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 6 DAY),5,'pending','Web','counter',DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 70 MINUTE)),(32,1,1,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 5 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 6 DAY),5,'pending','Web','qr',DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 61 MINUTE))");
        }
        try(var expiry=new quanlykhachsan.backend.booking.PendingBookingExpiryJob()){
            assertEquals(1,expiry.expire(c));assertEquals(0,expiry.expire(c));
            assertEquals(0,scalar(c,"SELECT balance FROM folios WHERE id=3").signum());
            assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id=3 AND status='cancelled'").intValueExact());
            assertEquals(2,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id IN (30,31) AND status='pending'").intValueExact());
            assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id=32 AND status='pending'").intValueExact(),"a booking inside the two-minute webhook grace must retain its room hold");
        }
        verifyHttpOrdersAndBank(c,schema,dao);
        // The readiness check uses the configured keys, so backfill the fixture with that same vault.
        var vault=quanlykhachsan.backend.customer.GuestIdentityVaultDao.configured();
        try(var s=c.createStatement()){s.execute("UPDATE customers SET identity_card='SYNTHETIC-ID-ONLY' WHERE id=1");}
        assertThrows(IllegalStateException.class,()->quanlykhachsan.backend.customer.IdentityVaultMigration.verifyReady(c));
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM customers WHERE identity_card='SYNTHETIC-ID-ONLY'").intValueExact());
        assertEquals(1,quanlykhachsan.backend.customer.IdentityVaultMigration.backfill(c,vault));
        quanlykhachsan.backend.customer.IdentityVaultMigration.verifyReady(c);
        assertEquals("SYNTHETIC-ID-ONLY",vault.readIdentity(c,1));
        assertArrayEquals(vault.blindIndex("SYNTHETIC-ID-ONLY"),vault.blindIndex("synthetic-id-only"));
        assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.execute("UPDATE customers SET identity_card='SYNTHETIC-REJECT' WHERE id=1");}});
        try(var s=c.createStatement()){
            s.execute("INSERT INTO promotions(name,code,discount_type,discount_value,start_date,end_date,applicable_scope,minimum_spend,max_discount,max_uses_per_user,condition_type,status) VALUES('Synthetic voucher','PHASE4-FNB','percentage',10,DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),'FNB',0,1000,1,'none','active')");
        }
        var discounted=order();discounted.setVoucherCode("PHASE4-FNB");
        assertEquals(new java.math.BigDecimal("22.23"),dao.previewOrder(discounted,1).getFinalPrice());
        discounted=order();discounted.setVoucherCode("PHASE4-FNB");dao.createOrderWithFolioDebit(discounted,UUID.randomUUID().toString(),1);
        var exhausted=order();exhausted.setVoucherCode("PHASE4-FNB");
        assertThrows(IllegalArgumentException.class,()->dao.previewOrder(exhausted,1));
        try(var s=c.createStatement()){s.execute("UPDATE promotions SET max_uses_per_user=100,condition_type='vip_only' WHERE code='PHASE4-FNB'");}
        assertThrows(IllegalArgumentException.class,()->dao.previewOrder(exhausted,1));
        try(var s=c.createStatement()){s.execute("UPDATE promotions SET condition_type='none',applicable_scope='SPA' WHERE code='PHASE4-FNB'");}
        assertThrows(IllegalArgumentException.class,()->dao.previewOrder(exhausted,1));
        try(var s=c.createStatement()){s.execute("UPDATE promotions SET applicable_scope='FNB',minimum_spend=100 WHERE code='PHASE4-FNB'");}
        assertThrows(IllegalArgumentException.class,()->dao.previewOrder(exhausted,1));
        try(var s=c.createStatement()){s.execute("UPDATE promotions SET minimum_spend=0,special_occasion='birthday' WHERE code='PHASE4-FNB'");}
        assertThrows(IllegalArgumentException.class,()->dao.previewOrder(exhausted,1));
        try(var s=c.createStatement()){s.execute("UPDATE customers SET date_of_birth=DATE_FORMAT(CURRENT_DATE,'2000-%m-%d') WHERE id=1");}
        assertEquals(new java.math.BigDecimal("22.23"),dao.previewOrder(exhausted,1).getFinalPrice());
        try(var s=c.createStatement()){s.execute("UPDATE promotions SET special_occasion=NULL,valid_after_checkout_days=1 WHERE code='PHASE4-FNB'");}
        assertThrows(IllegalArgumentException.class,()->dao.previewOrder(exhausted,1));
        System.out.println("Vault and voucher checks: non-destructive readiness, encryption, HMAC, plaintext constraint, decimal discount, usage, VIP, scope, spend, birthday and retention eligibility passed");
        try(var s=c.createStatement()){
            s.execute("INSERT INTO bookings(id,customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type) VALUES(5,1,1,DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),100,'confirmed','Web')");
            s.execute("INSERT INTO folios(id,booking_id,folio_type,balance,status,code) VALUES(5,5,'master',0,'open','TEST-NO-SHOW')");
        }
        var noShows=new quanlykhachsan.backend.booking.NoShowEngine(()->{var opened=DriverManager.getConnection(ApiConfig.getJdbcUrl(),ApiConfig.getDbUser(),ApiConfig.getDbPassword());opened.setCatalog(schema);return opened;});
        assertEquals(1,noShows.processBatch(java.time.LocalDate.now().atTime(3,0)));assertEquals(0,noShows.processBatch(java.time.LocalDate.now().atTime(3,0)));
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id=5 AND status='no_show'").intValueExact());
        assertEquals(1,scalar(c,"SELECT COUNT(*) FROM rooms WHERE id=1 AND status='occupied_clean'").intValueExact());
        System.out.println("Phase 4 real database checks: concurrent order replay, authoritative price, ownership, availability, rollback, cash replay, booking confirmation, mismatch, rate approval/audit passed");
    }
    private void verifyHttpOrdersAndBank(Connection c,String schema,quanlykhachsan.backend.hotelservice.ServiceOrderDAO dao)throws Exception {
        quanlykhachsan.backend.hotelservice.ServiceOrderDAO.ConnectionSource connections=()->{
            var opened=DriverManager.getConnection(ApiConfig.getJdbcUrl(),ApiConfig.getDbUser(),ApiConfig.getDbPassword());opened.setCatalog(schema);return opened;
        };
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var orders=server.createContext("/api/v1/service-orders",new quanlykhachsan.backend.hotelservice.ServiceOrderController(dao,connections));
        orders.getFilters().add(new JwtFilter());
        orders.getFilters().add(new com.sun.net.httpserver.Filter(){
            public String description(){return "Synthetic authenticated profile fixture";}
            public void doFilter(com.sun.net.httpserver.HttpExchange e,Chain chain)throws java.io.IOException {
                e.setAttribute("customer_id",e.getAttribute("userId"));chain.doFilter(e);
            }
        });
        String secret="synthetic-phase4-bank-secret-at-least-32-characters";
        var bank=new quanlykhachsan.backend.booking.FolioPaymentService(connections::open);
        server.createContext("/api/v1/webhooks/vietqr",new quanlykhachsan.backend.booking.BankWebhookController(secret,"1234567890",bank::settle));
        var revenue=server.createContext("/api/v1/revenue",new quanlykhachsan.backend.report.YieldRecommendationController(connections::open));revenue.getFilters().add(new JwtFilter());
        server.start();
        try{
            var client=java.net.http.HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.getAddress().getPort();
            String own=JwtTokenUtil.issueToken(1,"synthetic-customer","customer"),other=JwtTokenUtil.issueToken(2,"synthetic-other","customer");
            String body="{\"booking_id\":1,\"category\":\"FNB\",\"service_id\":1,\"quantity\":1,\"unit_price\":0.01}";
            String key=UUID.randomUUID().toString();
            assertEquals(401,post(client,base+"/api/v1/service-orders",body,null,key,null).statusCode());
            assertEquals(403,post(client,base+"/api/v1/service-orders",body,other,key,null).statusCode());
            assertEquals(400,post(client,base+"/api/v1/service-orders",body,own,null,null).statusCode());
            var quote=post(client,base+"/api/v1/service-orders/quote",body,own,null,null);assertEquals(200,quote.statusCode(),quote.body());
            long before=scalar(c,"SELECT COUNT(*) FROM service_orders").longValueExact();
            var created=post(client,base+"/api/v1/service-orders",body,own,key,null);assertEquals(201,created.statusCode(),created.body());
            var replay=post(client,base+"/api/v1/service-orders",body,own,key,null);assertEquals(201,replay.statusCode(),replay.body());
            var json=com.google.gson.JsonParser.parseString(created.body()).getAsJsonObject().getAsJsonObject("data");
            assertEquals(new java.math.BigDecimal("12.35"),json.get("final_price").getAsBigDecimal());
            assertEquals(json.get("order_id"),com.google.gson.JsonParser.parseString(replay.body()).getAsJsonObject().getAsJsonObject("data").get("order_id"));
            assertEquals(before+1,scalar(c,"SELECT COUNT(*) FROM service_orders").longValueExact());
            String invalid=body.substring(0,body.length()-1)+",\"scheduled_time\":\"not-a-date\"}";
            assertEquals(400,post(client,base+"/api/v1/service-orders",invalid,own,UUID.randomUUID().toString(),null).statusCode());
            try(var s=c.createStatement()){
                s.execute("UPDATE staff_collections SET amount=10 WHERE folio_id=2");
                s.execute("INSERT INTO bookings(id,customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type) VALUES(4,2,2,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 11 DAY),10,'pending','Web')");
                s.execute("INSERT INTO folios(id,booking_id,folio_type,balance,status,code) VALUES(4,4,'master',10,'open','TEST-BANK-4')");
                s.execute("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) VALUES(4,'debit','room_rate',10,CURRENT_DATE)");
            }
            String payload="{\"bank_trans_id\":\"SYNTHETIC-BANK-1\",\"reference_code\":\"TEST-BANK-4\",\"amount\":9.99,\"account_number\":\"1234567890\",\"trans_time\":\"2026-10-20T00:00:00Z\"}";
            String signature=HmacSignatureUtil.sign(secret,payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String bankUrl=base+"/api/v1/webhooks/vietqr";
            assertEquals(401,post(client,bankUrl,payload,null,null,null).statusCode());
            String wrong=payload.replace("1234567890","9999999999");
            assertEquals(400,post(client,bankUrl,wrong,null,null,HmacSignatureUtil.sign(secret,wrong.getBytes(java.nio.charset.StandardCharsets.UTF_8))).statusCode());
            assertEquals(200,post(client,bankUrl,payload,null,null,signature).statusCode());
            assertEquals(200,post(client,bankUrl,payload,null,null,signature).statusCode());
            assertEquals(new java.math.BigDecimal("0.01"),scalar(c,"SELECT balance FROM folios WHERE id=4"));
            assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id=4 AND status='pending'").intValueExact());
            assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bank_webhook_logs").intValueExact());
            String conflict=payload.replace("9.99","9.98");
            assertEquals(409,post(client,bankUrl,conflict,null,null,HmacSignatureUtil.sign(secret,conflict.getBytes(java.nio.charset.StandardCharsets.UTF_8))).statusCode());
            String finalPayment=payload.replace("BANK-1","BANK-2").replace("9.99","0.01");
            assertEquals(200,post(client,bankUrl,finalPayment,null,null,HmacSignatureUtil.sign(secret,finalPayment.getBytes(java.nio.charset.StandardCharsets.UTF_8))).statusCode());
            assertEquals(1,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id=4 AND status='confirmed'").intValueExact());
            assertTrue(new quanlykhachsan.backend.report.CollectionReconciliation().read(c,java.time.LocalDate.now()).reconciled());
            String admin=JwtTokenUtil.issueToken(42,"synthetic-approver","admin"),revenueManager=JwtTokenUtil.issueToken(43,"synthetic-revenue","revenue_manager");
            String forecastUrl=base+"/api/v1/revenue/forecast?days=1";
            var denied=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(forecastUrl)).header("Authorization","Bearer "+own).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(403,denied.statusCode());
            var insufficient=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(forecastUrl)).header("Authorization","Bearer "+admin).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,insufficient.statusCode(),insufficient.body());assertTrue(insufficient.body().contains("INSUFFICIENT_HISTORY"));
            var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            try(var q=c.prepareStatement("INSERT INTO daily_business_reports(audit_date,room_revenue,occupied_rooms,available_rooms,snapshot_json) VALUES(?,100,1,2,'{\"collections\":{\"reconciled\":true}}')")){
                for(int i=1;i<=7;i++){q.setDate(1,java.sql.Date.valueOf(today.minusDays(i)));q.executeUpdate();}
            }
            // Distinct cache key ensures the new persisted history is read.
            var forecast=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/revenue/forecast?days=2")).header("Authorization","Bearer "+revenueManager).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,forecast.statusCode(),forecast.body());
            var rows=com.google.gson.JsonParser.parseString(forecast.body()).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("forecast");
            assertEquals(new java.math.BigDecimal("100.00"),rows.get(0).getAsJsonObject().get("adr").getAsBigDecimal());
            assertEquals(new java.math.BigDecimal("50.00"),rows.get(0).getAsJsonObject().get("revpar").getAsBigDecimal());
            String approval="{\"rates\":[{\"room_type_id\":1,\"date\":\""+today.plusDays(1)+"\",\"multiplier\":1.20,\"version\":0}]}";
            assertEquals(403,post(client,base+"/api/v1/revenue/apply-rates",approval,revenueManager,null,null).statusCode());
            var applied=post(client,base+"/api/v1/revenue/apply-rates",approval,admin,null,null);assertEquals(200,applied.statusCode(),applied.body());
            assertEquals(409,post(client,base+"/api/v1/revenue/apply-rates",approval,admin,null,null).statusCode());
            assertEquals(1,scalar(c,"SELECT COUNT(*) FROM pricing_rule_audit WHERE actor_id=42 AND rate_date=DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY) AND created_at IS NOT NULL").intValueExact());
            System.out.println("Phase 4 real HTTP/database checks: authenticated order, IDOR, quote, retry, invalid schedule, beneficiary/signature validation, partial payment, bank replay/conflict and paid confirmation passed");
            System.out.println("Revenue HTTP checks: role denial, explicit insufficient history, persisted ADR/RevPAR, advisory-only forecast, approval, version conflict and approver/effective-date audit passed");
        }finally{server.stop(0);}
    }
    private java.net.http.HttpResponse<String> post(java.net.http.HttpClient client,String url,String body,String token,String key,String signature)throws Exception {
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(10)).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);if(key!=null)request.header("Idempotency-Key",key);if(signature!=null)request.header("X-Signature",signature);
        return client.send(request.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    private quanlykhachsan.backend.hotelservice.ServiceOrder order(){
        var order=new quanlykhachsan.backend.hotelservice.ServiceOrder();order.setBookingId(1);order.setServiceId(1);order.setCategory("FNB");order.setQuantity(2);order.setUnitPrice(new java.math.BigDecimal("0.01"));return order;
    }
    private java.math.BigDecimal scalar(Connection c,String sql)throws SQLException{
        try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getBigDecimal(1);}
    }
    static void execute(Connection c,String sql)throws SQLException {
        String delimiter=";";var pending=new StringBuilder();
        try(var statement=c.createStatement()){
            for(String line:sql.split("\\R")){
                String trimmed=line.trim();if(trimmed.startsWith("--")||trimmed.startsWith("#")||trimmed.isEmpty())continue;
                if(trimmed.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")){delimiter=trimmed.substring(10).trim();continue;}
                pending.append(line).append('\n');String candidate=pending.toString().trim();
                if(candidate.endsWith(delimiter)){
                    statement.execute(candidate.substring(0,candidate.length()-delimiter.length()));pending.setLength(0);
                }
            }
            if(!pending.toString().isBlank())throw new SQLException("Unterminated bootstrap SQL");
        }
    }
}
