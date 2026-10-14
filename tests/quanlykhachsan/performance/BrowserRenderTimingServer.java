package quanlykhachsan.performance;

import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import quanlykhachsan.backend.utils.StaticFileHandler;

/** Local test-only instrumentation; measures browser rendering, never tool round-trip time. */
public final class BrowserRenderTimingServer {
    public static void main(String[] args)throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",8299),0);
        server.createContext("/booking/performance.html",e->{
            String html=Files.readString(Path.of("web-ui/public/index.html"),StandardCharsets.UTF_8)
                .replace("<head>","<head><base href='/booking/'><script defer src='/booking/performance-probe.js'></script>");
            send(e,"text/html; charset=UTF-8",html);
        });
        server.createContext("/booking/performance-probe.js",e->send(e,"application/javascript; charset=UTF-8","""
            window.addEventListener('load', () => requestAnimationFrame(() => requestAnimationFrame(() => {
              const nav=performance.getEntriesByType('navigation')[0];
              const paint=performance.getEntriesByName('first-contentful-paint')[0];
              document.documentElement.dataset.phase2RenderMs=String(Math.max(nav?.loadEventEnd ?? 0,performance.now()));
              document.documentElement.dataset.phase2FirstPaintMs=String(paint?.startTime ?? 0);
              document.documentElement.dataset.phase2Viewport=String(innerWidth);
              document.documentElement.dataset.phase2ContentWidth=String(document.documentElement.scrollWidth);
            })));
            """));
        server.createContext("/",new StaticFileHandler(Path.of("web-ui/public")));
        server.start();System.out.println("Test-only browser timing: http://127.0.0.1:8299/booking/performance.html");
        try { int input;while((input=System.in.read())!=-1 && input!=3) { } }finally{server.stop(0);}
    }
    private static void send(HttpExchange e,String contentType,String body)throws java.io.IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",contentType);e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}e.close();
    }
}
