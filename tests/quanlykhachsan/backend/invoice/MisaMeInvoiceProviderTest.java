package quanlykhachsan.backend.invoice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MisaMeInvoiceProviderTest {
    private HttpServer server;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CapturedRequest> requests = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void startServer() throws IOException {
        responses.clear();
        requests.clear();
        responses.put("/api/v3/auth/token", success("api-token"));
        responses.put("/api/integration/auth/token", success("integration-token"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new CapturedRequest(path, exchange.getRequestURI().getRawQuery(), body,
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("CompanyTaxCode")));
            byte[] bytes = responses.getOrDefault(path, "{\"Success\":false,\"ErrorCode\":\"UNEXPECTED_PATH\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void hsmCodePublicationUsesDocumentedRouteAndCachesApiToken() throws Exception {
        String publishPath = "/api/v3/code/itg/invoicepublishing/publishhsm";
        responses.put(publishPath, publishedResponse());
        MisaMeInvoiceProvider provider = provider(MisaMeInvoiceProvider.Mode.HSM_CODE);
        JsonObject invoice = invoiceData("REF-HSM");
        JsonObject reference = new JsonObject();
        reference.addProperty("ReferenceType", 2);

        assertEquals("TX-42", provider.publish(invoice, "Guest", "guest@example.test", reference).transactionId());
        assertEquals("TX-42", provider.publish(invoice, "Guest", "guest@example.test").transactionId());

        List<CapturedRequest> sent = List.copyOf(requests);
        assertEquals(1, countPath(sent, "/api/v3/auth/token"));
        assertEquals(2, countPath(sent, publishPath));
        CapturedRequest publish = sent.stream().filter(r -> r.path.equals(publishPath)).findFirst().orElseThrow();
        assertEquals("Bearer api-token", publish.authorization);
        assertEquals("TAX-TEST-123", publish.companyTaxCode);
        assertFalse(publish.body.contains("fixture-password"));
        JsonArray body = JsonParser.parseString(publish.body).getAsJsonArray();
        JsonObject item = body.get(0).getAsJsonObject();
        assertEquals("REF-HSM", item.get("RefID").getAsString());
        assertEquals(2, item.getAsJsonObject("OriginalInvoiceData").get("ReferenceType").getAsInt());
        assertFalse(invoice.has("ReferenceType"), "reference fields should not mutate the caller's invoice object");
    }

    @Test
    void posPublicationUsesDocumentedRouteAndInvoiceMarker() throws Exception {
        String publishPath = "/api/v3/code/itg/invoice-calculating/invoiceandpublish";
        responses.put(publishPath, publishedResponse());
        JsonObject invoice = invoiceData("REF-POS");

        provider(MisaMeInvoiceProvider.Mode.POS).publish(invoice, "Guest", "guest@example.test");

        CapturedRequest publish = requests.stream().filter(r -> r.path.equals(publishPath)).findFirst().orElseThrow();
        JsonObject item = JsonParser.parseString(publish.body).getAsJsonArray().get(0).getAsJsonObject();
        JsonObject orgInvoice = item.getAsJsonObject("OrgInvoiceData");
        assertTrue(orgInvoice.has("IsInvoiceCalculatingMachine"), "MISA POS contract requires the machine marker");
        assertTrue(orgInvoice.get("IsInvoiceCalculatingMachine").getAsBoolean());
        assertEquals("REF-POS", orgInvoice.get("RefID").getAsString());
        assertFalse(item.has("RefID"), "POS requests carry RefID inside OrgInvoiceData");
    }

    @Test
    void hsmWithoutTaxCodeUsesUncodedStatusRoute() throws Exception {
        String statusPath = "/api/v3/itg/invoicepublished/invoicestatus";
        responses.put(statusPath, success("[{\"TransactionID\":\"TX-7\",\"PublishStatus\":1,\"InvoiceCode\":null,\"SendTaxStatus\":2,\"IsDelete\":false}]"));

        MisaMeInvoiceProvider.Status status = provider(MisaMeInvoiceProvider.Mode.HSM_NO_CODE).status("TX-7");

        assertEquals("TX-7", status.transactionId());
        assertEquals(2, status.sendTaxStatus());
        CapturedRequest request = requests.stream().filter(r -> r.path.equals(statusPath)).findFirst().orElseThrow();
        assertEquals("TAX-TEST-123", request.companyTaxCode);
        assertEquals("[\"TX-7\"]", request.body);
    }

    @Test
    void viewUrlAllowsMisaDomainAndRejectsLookalikeHost() throws Exception {
        String viewPath = "/api/v3/code/itg/invoicepublished/linkview";
        MisaMeInvoiceProvider provider = provider(MisaMeInvoiceProvider.Mode.HSM_CODE);
        responses.put(viewPath, success("https://www.meinvoice.vn/tra-cuu/invoice?id=TX-8"));
        assertEquals("https://www.meinvoice.vn/tra-cuu/invoice?id=TX-8", provider.viewUrl("TX-8"));

        responses.put(viewPath, success("https://meinvoice.vn.attacker.invalid/invoice?id=TX-8"));
        MisaMeInvoiceProvider.ProviderException error = assertThrows(
                MisaMeInvoiceProvider.ProviderException.class, () -> provider.viewUrl("TX-8"));
        assertEquals("INVALID_PROVIDER_LINK", error.code());
    }

    @Test
    void pdfDownloadAcceptsPdfBytesAndRejectsNonPdfData() throws Exception {
        String path = "/api/v3/code/itg/invoicepublished/downloadinvoice";
        byte[] pdf = "%PDF-1.4 synthetic".getBytes(StandardCharsets.US_ASCII);
        responses.put(path, pdfResponse(pdf));
        MisaMeInvoiceProvider provider = provider(MisaMeInvoiceProvider.Mode.HSM_CODE);

        assertArrayEquals(pdf, provider.downloadPdf("TX-9").pdf());
        assertEquals("downloadDataType=PDF", requests.stream().filter(r -> r.path.equals(path)).findFirst().orElseThrow().query);

        responses.put(path, pdfResponse("not a pdf".getBytes(StandardCharsets.US_ASCII)));
        MisaMeInvoiceProvider.ProviderException error = assertThrows(
                MisaMeInvoiceProvider.ProviderException.class, () -> provider.downloadPdf("TX-9"));
        assertEquals("PDF_INVALID", error.code());
    }

    @Test
    void cancellationUsesSeparateIntegrationTokenAndDocumentedRoute() throws Exception {
        String cancelPath = "/api/integration/invoice/cancel";
        responses.put(cancelPath, "{\"Success\":true,\"ErrorCode\":null}");

        provider(MisaMeInvoiceProvider.Mode.HSM_CODE).cancel("TX-10", "1C26TAA", "Guest requested cancellation");

        CapturedRequest token = requests.stream().filter(r -> r.path.equals("/api/integration/auth/token")).findFirst().orElseThrow();
        CapturedRequest cancel = requests.stream().filter(r -> r.path.equals(cancelPath)).findFirst().orElseThrow();
        assertEquals("Bearer integration-token", cancel.authorization);
        assertEquals("TX-10", JsonParser.parseString(cancel.body).getAsJsonObject().get("TransactionID").getAsString());
        assertFalse(cancel.body.contains("fixture-password"));
        assertTrue(token.body.contains("fixture-password"));
    }

    private MisaMeInvoiceProvider provider(MisaMeInvoiceProvider.Mode mode) {
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        MisaMeInvoiceProvider.Settings settings = new MisaMeInvoiceProvider.Settings("test", origin,
                origin + "/api/v3", mode, "fixture-app-id", "TAX-TEST-123", "fixture-user",
                "fixture-password", "1C26TAA", "template-1", "VAT invoice", "Synthetic Hotel",
                "Synthetic address", "", "");
        return new MisaMeInvoiceProvider(settings);
    }

    private static JsonObject invoiceData(String refId) {
        JsonObject data = new JsonObject();
        data.addProperty("RefID", refId);
        data.addProperty("BuyerLegalName", "Synthetic Guest");
        return data;
    }

    private static String publishedResponse() {
        JsonArray data = new JsonArray();
        JsonObject issued = new JsonObject();
        issued.addProperty("RefID", "REF-TEST");
        issued.addProperty("TransactionID", "TX-42");
        issued.addProperty("InvTemplateNo", "1");
        issued.addProperty("InvSeries", "1C26TAA");
        issued.addProperty("InvNo", "00000042");
        issued.addProperty("InvDate", "2026-10-23T00:00:00+07:00");
        issued.addProperty("ErrorCode", "");
        data.add(issued);
        return success(com.google.gson.JsonParser.parseString(data.toString()).toString());
    }

    private static String pdfResponse(byte[] pdf) {
        JsonArray data = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("Data", Base64.getEncoder().encodeToString(pdf));
        data.add(item);
        return success(data.toString());
    }

    private static String success(String data) {
        JsonObject root = new JsonObject();
        root.addProperty("Success", true);
        root.add("ErrorCode", com.google.gson.JsonNull.INSTANCE);
        root.addProperty("Data", data);
        return root.toString();
    }

    private static long countPath(List<CapturedRequest> sent, String path) {
        return sent.stream().filter(request -> request.path.equals(path)).count();
    }

    private record CapturedRequest(String path, String query, String body, String authorization, String companyTaxCode) { }
}
