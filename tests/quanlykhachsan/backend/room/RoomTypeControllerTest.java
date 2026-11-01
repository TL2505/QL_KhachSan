package quanlykhachsan.backend.room;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import static org.junit.jupiter.api.Assertions.*;

class RoomTypeControllerTest {
    @Test void roomTypeChangesRequireAdminAndRejectInvalidCatalogDataBeforeDatabaseWrite() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/rooms",new RoomController()).getFilters().add(new JwtFilter());server.start();
        try {
            String endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/rooms/types";
            assertEquals(401,send(endpoint,null,"POST","{}").statusCode());
            assertEquals(403,send(endpoint,JwtTokenUtil.issueToken(12,"staff-test","STAFF"),"POST","{}").statusCode());
            String admin=JwtTokenUtil.issueToken(11,"admin-test","ADMIN");
            assertEquals(400,send(endpoint,admin,"POST","{\"name\":\"x\",\"basePrice\":1000,\"capacity\":2}").statusCode());
            assertEquals(400,send(endpoint+"/4",admin,"PUT","{\"name\":\"Valid Name\",\"basePrice\":1000,\"capacity\":2,\"imageUrl\":\"javascript:alert(1)\"}").statusCode());
            assertEquals(400,send(endpoint+"/4",admin,"PUT","{\"name\":\"Valid Name\",\"basePrice\":1000,\"capacity\":101}").statusCode());
        } finally { server.stop(0); }
    }

    private static HttpResponse<String> send(String url,String token,String method,String body)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        var request=builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
    }
}
