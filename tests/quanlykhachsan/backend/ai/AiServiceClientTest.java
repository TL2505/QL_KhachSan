package quanlykhachsan.backend.ai;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiServiceClientTest {
    @Test void productionRejectsPlainHttpAndRedirects() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new AiServiceClient(URI.create("http://ai-service:8082"),SSLContext.getDefault()));
    }
    @Test void deadlinesDisconnectOversizeAndSuccessAreExplicit() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/ocr", e->{byte[] bytes="{\"synthetic\":true}".getBytes();e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});
        server.createContext("/face-match",e->{byte[] bytes=new byte[70_000];e.sendResponseHeaders(200,bytes.length);try{e.getResponseBody().write(bytes);}finally{e.close();}});
        var release=new java.util.concurrent.CountDownLatch(1);
        server.createContext("/forecast",e->{try{release.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{e.close();}});
        server.createContext("/rag/query",e->{e.getResponseHeaders().set("Location","/ocr");e.sendResponseHeaders(302,-1);e.close();});
        server.start();
        var origin=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
        var client=new AiServiceClient(origin,HttpClient.newHttpClient(),Duration.ofMillis(200));
        try {
            assertEquals("{\"synthetic\":true}",client.post("/ocr","{}"));
            assertThrows(java.io.IOException.class,()->client.post("/face-match","{}"));
            assertThrows(java.io.IOException.class,()->client.post("/rag/query","{}"));
            long start=System.nanoTime();
            assertThrows(java.io.IOException.class,()->client.post("/forecast","{}"));
            assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1000);
        }finally{release.countDown();server.stop(0);executor.shutdownNow();}
        assertThrows(java.io.IOException.class,()->client.post("/ocr","{}"));
    }
}
