package quanlykhachsan.stubs;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import quanlykhachsan.backend.utils.CorsFilter;

/** Explicit development-only Day-1 server; never part of the production source tree. */
public final class MockApiServer implements AutoCloseable {
    private final HttpServer server;
    private final java.util.concurrent.atomic.AtomicBoolean settled=new java.util.concurrent.atomic.AtomicBoolean();
    private java.math.BigDecimal mockBalance=new java.math.BigDecimal("2268000.00");
    public MockApiServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/v1/", this::handle).getFilters().add(new CorsFilter());
        server.createContext("/",new quanlykhachsan.backend.utils.StaticFileHandler(java.nio.file.Path.of("web-ui","public")));
        server.createContext("/mock/fire-webhook",e->{
            try {
                var query=quanlykhachsan.backend.utils.PortalValidation.query(e);String backend=System.getenv("MOCK_WEBHOOK_BACKEND");
                if(backend==null){settled.set(true);write(e,200,"{\"status\":\"MOCK_SETTLED\"}");}
                else {
                    JsonObject payload=new JsonObject();payload.addProperty("bank_trans_id",query.getOrDefault("bank_trans_id","TXN-MOCK-1"));payload.addProperty("amount",new java.math.BigDecimal(query.getOrDefault("amount",mockBalance.toPlainString())));payload.addProperty("reference_code",query.getOrDefault("reference_code","FOLIO-MASTER-MOCK-999"));payload.addProperty("trans_time",java.time.OffsetDateTime.now().toString());
                    var response=new BankWebhookSimulator().fire(java.net.URI.create(backend),System.getenv("BANK_WEBHOOK_SECRET"),payload);write(e,response.statusCode(),response.body());
                }
            }catch(Exception ex){write(e,503,error("Test webhook dispatch failed"));}finally{e.close();}
        });
    }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); }
    public static void main(String[] args) throws Exception {
        var mock = new MockApiServer(8089);
        mock.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mock::close));
        System.out.println("Week 5 mock API at http://127.0.0.1:8089; fixed test OTP 123456");
    }

    private void handle(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath();
        try {
            if(PricingAndReportMockStubs.handle(e))return;
            if(HousekeepingMockStubs.handle(e))return;
            if(path.equals("/api/v1/rooms") && e.getRequestMethod().equals("GET")){write(e,200,"{\"status\":\"success\",\"data\":[{\"id\":101,\"room_number\":\"101\",\"status\":\"vacant_dirty\",\"version\":0},{\"id\":102,\"room_number\":\"102\",\"status\":\"occupied_clean\",\"version\":0}]}");return;}
            if(path.matches("/api/v1/folios/999/(vietqr|payment-status)") && e.getRequestMethod().equals("GET")) {
                if(path.endsWith("payment-status"))write(e,200,"{\"status\":\"success\",\"data\":{\"folio_id\":999,\"status\":\""+(settled.get()?"settled":"open")+"\",\"balance\":"+(settled.get()?"0":mockBalance)+"}}");
                else {
                    var qr=new quanlykhachsan.backend.booking.VietQrGenerator().generate("970422","0901234567899",mockBalance,"FOLIO-MASTER-MOCK-999");
                    JsonObject data=new JsonObject();data.addProperty("qr_code_string",qr.qr_code_string());data.addProperty("qr_image_base64",qr.qr_image_base64());data.addProperty("amount",mockBalance);data.addProperty("beneficiary_bank","MBBank (Mock)");data.addProperty("account_number","0901234567899");data.addProperty("account_holder","TEST BENEFICIARY");data.addProperty("reference_code","FOLIO-MASTER-MOCK-999");data.addProperty("expires_in_seconds",300);
                    write(e,200,quanlykhachsan.backend.utils.ApiResponseUtil.successWithData(data));
                }return;
            }
            if(path.equals("/api/v1/pricing/quote") && e.getRequestMethod().equals("GET")) {
                var query=quanlykhachsan.backend.utils.PortalValidation.query(e);
                var range=quanlykhachsan.backend.utils.PortalValidation.range(query.get("check_in"),query.get("check_out"));
                long nights=java.time.temporal.ChronoUnit.DAYS.between(range.checkIn(),range.checkOut());
                var quote=quanlykhachsan.backend.booking.FinancialCalculator.calculate(new java.math.BigDecimal("1000000").multiply(java.math.BigDecimal.valueOf(nights)),java.math.BigDecimal.ZERO,quanlykhachsan.backend.booking.TaxConfig.standardEightPercentVat());
                write(e,200,"{\"status\":\"success\",\"data\":{\"total_amount\":"+quote.getTotalAmount()+",\"nightly_rates\":[{\"multiplier\":1.0}]}}");return;
            }
            if (path.equals("/api/v1/rooms/availability") && e.getRequestMethod().equals("GET")) {
                write(e, 200, "{\"status\":\"success\",\"data\":[{\"id\":101,\"room_number\":\"101\",\"room_type_id\":1,\"room_type_name\":\"Deluxe (Mock)\",\"version\":0,\"price\":1000000,\"status\":\"vacant_clean\"}]}");
                return;
            }
            if (!e.getRequestMethod().equals("POST")) { write(e, 405, error("Method not allowed")); return; }
            byte[] bytes = e.getRequestBody().readNBytes(16385);
            if (bytes.length > 16384) { write(e, 413, error("Payload too large")); return; }
            JsonObject body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (path.equals("/api/v1/auth/guest-otp/request")) {
                if (!phone(body)) { write(e, 400, error("Invalid phone")); return; }
                write(e, 200, "{\"status\":\"success\",\"data\":{\"status\":\"OTP_SENT\",\"expires_in_seconds\":300}}");
            } else if (path.equals("/api/v1/auth/guest-otp/verify")) {
                String code = body.has("otp_code") ? body.get("otp_code").getAsString()
                    : body.has("otp") ? body.get("otp").getAsString() : "";
                if (!phone(body) || !code.equals("123456")) { write(e, 400, error("Invalid OTP")); return; }
                write(e, 200, fixture("mock-guest-otp.json"));
            } else if (path.equals("/api/v1/bookings/public")) {
                String key = e.getRequestHeaders().getFirst("Idempotency-Key");
                if (key == null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
                    write(e, 400, error("UUIDv4 Idempotency-Key required")); return;
                }
                if (!phone(body)) { write(e, 400, error("Invalid phone")); return; }
                JsonObject booking=JsonParser.parseString(fixture("mock-public-booking.json")).getAsJsonObject();
                if(body.has("check_in_date") && body.has("check_out_date")) {
                    var range=quanlykhachsan.backend.utils.PortalValidation.range(body.get("check_in_date").getAsString().substring(0,10),body.get("check_out_date").getAsString().substring(0,10));
                    long nights=java.time.temporal.ChronoUnit.DAYS.between(range.checkIn(),range.checkOut());
                    mockBalance=quanlykhachsan.backend.booking.FinancialCalculator.calculate(new java.math.BigDecimal("1000000").multiply(java.math.BigDecimal.valueOf(nights)),java.math.BigDecimal.ZERO,quanlykhachsan.backend.booking.TaxConfig.standardEightPercentVat()).getTotalAmount();
                    if(body.has("voucher_code")){if(!body.get("voucher_code").getAsString().equals("SUMMER50")){write(e,400,error("Invalid synthetic voucher"));return;}var room=new java.math.BigDecimal("1000000").multiply(java.math.BigDecimal.valueOf(nights));mockBalance=quanlykhachsan.backend.booking.FinancialCalculator.calculate(room.subtract(room.multiply(new java.math.BigDecimal("0.50")).min(new java.math.BigDecimal("500000"))),java.math.BigDecimal.ZERO,quanlykhachsan.backend.booking.TaxConfig.standardEightPercentVat()).getTotalAmount();}
                    booking.getAsJsonObject("data").getAsJsonObject("folio").addProperty("balance",mockBalance);
                }
                settled.set(false);write(e, 201, booking.toString());
            } else write(e, 404, error("Mock endpoint not found"));
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException ex) {
            write(e, 400, error("Invalid JSON payload"));
        } finally { e.close(); }
    }
    private static boolean phone(JsonObject body) {
        return body.has("phone") && !body.get("phone").isJsonNull()
            && body.get("phone").getAsString().matches("(?:0|\\+84)[35789][0-9]{8}");
    }
    private static String fixture(String name) throws IOException {
        try (InputStream input = MockApiServer.class.getResourceAsStream("/mock-server/" + name)) {
            if (input == null) throw new IOException("Missing checked-in mock fixture");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static String error(String message) {
        JsonObject object = new JsonObject(); object.addProperty("status", "error"); object.addProperty("message", message);
        return object.toString();
    }
    private static void write(HttpExchange e, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        e.sendResponseHeaders(status, bytes.length); e.getResponseBody().write(bytes);
    }
}
