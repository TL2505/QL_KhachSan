package quanlykhachsan.backend.interaction;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import static org.junit.jupiter.api.Assertions.*;

class ChatControllerTest {
    @Test void supportQueueAndConversationOwnershipUseAuthenticatedIdentity() throws Exception {
        List<Message> stored = new ArrayList<>();
        var service = new ChatService() {
            @Override public void sendMessage(Message msg) {stored.add(msg);}
            @Override public List<Message> getConversation(int u1,int u2) {return List.of();}
            @Override public List<Message> getInboxes(int staffId) {return List.of();}
        };
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/chat",new ChatController(service)).getFilters().add(new JwtFilter());server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/chat";
            var client=HttpClient.newHttpClient();
            String customer=JwtTokenUtil.issueToken(3,"customer","CUSTOMER"),staff=JwtTokenUtil.issueToken(2,"staff","STAFF");
            assertEquals(200,request(client,base+"/history?u1=3&u2=0",customer,null));
            assertEquals(403,request(client,base+"/history?u1=4&u2=0",customer,null));
            assertEquals(403,request(client,base+"/history?u1=3&u2=2",customer,null));
            assertEquals(403,request(client,base+"/inbox?staffId=2",customer,null));
            assertEquals(200,request(client,base+"/inbox?staffId=2",staff,null));
            assertEquals(403,request(client,base+"/inbox?staffId=1",staff,null));
            assertEquals(200,request(client,base+"/history?u1=2&u2=3",staff,null));
            assertEquals(401,request(client,base+"/history?u1=3&u2=0",null,null));
            String message="{\"senderId\":3,\"receiverId\":0,\"content\":\" Hỗ trợ \"}";
            assertEquals(200,request(client,base+"/send",customer,message));
            assertEquals(1,stored.size());assertEquals(0,stored.get(0).getReceiverId());assertEquals("Hỗ trợ",stored.get(0).getContent());
            assertEquals(403,request(client,base+"/send",customer,message.replace("\"senderId\":3","\"senderId\":2")));
            assertEquals(403,request(client,base+"/send",customer,message.replace("\"receiverId\":0","\"receiverId\":2")));
            assertEquals(400,request(client,base+"/send",customer,message.replace(" Hỗ trợ "," ")));
            assertEquals(400,request(client,base+"/send",customer,message.replace(" Hỗ trợ ","x".repeat(2001))));
            assertEquals(200,request(client,base+"/send",staff,"{\"senderId\":2,\"receiverId\":3,\"content\":\"Xin chào\"}"));
            assertEquals(2,stored.size());
        } finally {server.stop(0);}
    }
    private int request(HttpClient client,String url,String token,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(url)).header("X-User-Role","ADMIN");
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body==null)request.GET();else request.POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
