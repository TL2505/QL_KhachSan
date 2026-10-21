package quanlykhachsan.backend.interaction;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.JwtFilter;
import static org.junit.jupiter.api.Assertions.*;
class PublicReviewAccessTest {
    private static HttpResponse<String> request(String path,String method,AtomicInteger reads)throws Exception {
        var controller=new ReviewController(new ReviewService(){
            @Override public JsonObject getPublicReputationSummary(int page,int limit){reads.incrementAndGet();assertEquals(1,page);assertEquals(6,limit);return new JsonObject();}
        });
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/reviews",controller).getFilters().add(new JwtFilter());server.start();
        try{return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+path))
            .header("X-User-Role","STAFF").method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());}
        finally{server.stop(0);}
    }
    @Test void anonymousSummaryAndItsTrailingSlashStayOnThePublicReadHandler()throws Exception {
        var reads=new AtomicInteger();
        for(String path:new String[]{"/api/v1/reviews/public?page=1&limit=6","/api/v1/reviews/public/?page=1&limit=6"})assertEquals(200,request(path,"GET",reads).statusCode());
        assertEquals(2,reads.get());
    }
    @Test void summaryPathCannotBeUsedToBypassReviewWriteAuthorization()throws Exception {
        var reads=new AtomicInteger();
        for(String method:new String[]{"POST","PUT","DELETE"})assertEquals(405,request("/api/v1/reviews/public",method,reads).statusCode());
        assertEquals(0,reads.get());
    }
    @Test void protectedReviewCollectionStillRequiresJwtDespiteSpoofedRoleHeader()throws Exception {
        var reads=new AtomicInteger();
        for(String method:new String[]{"GET","POST","DELETE"})assertEquals(401,request("/api/v1/reviews",method,reads).statusCode());
        assertEquals(0,reads.get());
    }
}
