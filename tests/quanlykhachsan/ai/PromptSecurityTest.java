package quanlykhachsan.ai;

import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.ai.PromptGuardrailFilter;
import static org.junit.jupiter.api.Assertions.*;

class PromptSecurityTest {
    @Test void hundredAttackSamplesBlockedBeforeModelAndBenignMessagesPass() throws Exception {
        var attacks=Files.readAllLines(Path.of("tests/resources/ai/jailbreak_payloads.txt"));
        assertEquals(100,attacks.size()); assertEquals(100,attacks.stream().distinct().count());
        var called = new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var context=server.createContext("/chat",e->{called.incrementAndGet();e.sendResponseHeaders(204,-1);e.close();});
        context.getFilters().add(new PromptGuardrailFilter()); server.start();
        try {
            var client=HttpClient.newHttpClient(); var uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat");
            for(String payload:attacks) {
                var response=post(client,uri,new Gson().toJson(Map.of("message",payload)));
                assertEquals(400,response.statusCode(),payload);
                assertTrue(response.body().contains("PROMPT_INJECTION_DETECTED"));
            }
            assertEquals(0,called.get(),"No attack may reach the model handler");
            for(String benign:new String[]{"What time is check-in?","Quy định trả phòng muộn?","Please contact the manager","Where is the pool?","I need a refund"})
                assertEquals(204,post(client,uri,new Gson().toJson(Map.of("message",benign))).statusCode());
            assertEquals(5,called.get());
            assertEquals(400,post(client,uri,"{broken").statusCode());
            assertEquals(413,post(client,uri,"x".repeat(17000)).statusCode());
            assertEquals(5,called.get());
        } finally {server.stop(0);}
    }
    private HttpResponse<String> post(HttpClient client,URI uri,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
