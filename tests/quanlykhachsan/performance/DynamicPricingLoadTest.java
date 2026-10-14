package quanlykhachsan.performance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import com.sun.net.httpserver.HttpServer;
import quanlykhachsan.backend.pricing.PricingController;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.support.Phase2TestDatabase;
class DynamicPricingLoadTest {
    @Test void tenThousandQuotesWithFiftyConcurrentClients()throws Exception {
        // A fresh JVM makes the performance gate independent of PDF/font caches,
        // short-lived HTTP clients and executor lifetimes in preceding functional tests.
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Files.createDirectories(Path.of("target","phase2-artifacts"));Path log=Path.of("target","phase2-artifacts","pricing-load-process.log");
        Process process=new ProcessBuilder(java,"-Dfile.encoding=UTF-8","-Dsun.stdout.encoding=UTF-8","-Dsun.stderr.encoding=UTF-8","-cp",classpath,DynamicPricingLoadTest.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();fail("Isolated pricing benchmark timed out");}
        String output=Files.readString(log);System.out.print(output);assertEquals(0,process.exitValue(),output);
    }
    public static void main(String[] args)throws Exception {new DynamicPricingLoadTest().benchmark();}
    private void benchmark()throws Exception {
        Phase2TestDatabase.require();int type=(int)Phase2TestDatabase.scalar("SELECT MIN(id) FROM room_types");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var handlers=Executors.newFixedThreadPool(20);server.setExecutor(handlers);var context=server.createContext("/api/v1/pricing/quote",new PricingController());context.getFilters().addAll(List.of(new quanlykhachsan.backend.utils.GlobalExceptionHandler(),new quanlykhachsan.backend.utils.CorsFilter(),new quanlykhachsan.backend.utils.JwtFilter()));server.start();
        var workers=Executors.newFixedThreadPool(50);var httpCallbacks=Executors.newFixedThreadPool(50);var client=HttpClient.newBuilder().executor(httpCallbacks).connectTimeout(Duration.ofSeconds(5)).build();
        var day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2);var uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/pricing/quote?room_type_id="+type+"&check_in="+day+"&check_out="+day.plusDays(1));
        long[] latencies=new long[10000];var start=new CountDownLatch(1);List<Future<?>> futures=new ArrayList<>();
        try {
            for(int i=0;i<100;i++)assertEquals(200,client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            for(int worker=0;worker<50;worker++){int offset=worker*200;futures.add(workers.submit(()->{
                start.await();for(int n=0;n<200;n++){long before=System.nanoTime();var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.discarding());latencies[offset+n]=System.nanoTime()-before;if(response.statusCode()!=200)throw new IllegalStateException("Pricing HTTP "+response.statusCode());}return null;
            }));}
            start.countDown();for(var future:futures)future.get(180,TimeUnit.SECONDS);
            Arrays.sort(latencies);double p95=latencies[9499]/1000000.0,p99=latencies[9899]/1000000.0;
            String result=String.format(Locale.ROOT,"requests=10000 concurrency=50 p95_ms=%.3f p99_ms=%.3f active_connections=%d awaiting_connections=%d%n",p95,p99,DatabaseConnection.activeConnections(),DatabaseConnection.awaitingConnections());
            Files.createDirectories(Path.of("target","phase2-artifacts"));Files.writeString(Path.of("target","phase2-artifacts","pricing-load.txt"),result);System.out.print(result);
            assertEquals(0,DatabaseConnection.activeConnections());assertEquals(0,DatabaseConnection.awaitingConnections());
            assertTrue(p95<15,"P95 target <15ms: "+result);assertTrue(p99<30,"P99 target <30ms: "+result);
        }finally{workers.shutdownNow();server.stop(0);handlers.shutdownNow();httpCallbacks.shutdownNow();workers.awaitTermination(5,TimeUnit.SECONDS);handlers.awaitTermination(5,TimeUnit.SECONDS);httpCallbacks.awaitTermination(5,TimeUnit.SECONDS);}
    }
}
