package quanlykhachsan.backend.customer;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import static org.junit.jupiter.api.Assertions.*;

class LoyaltyDirectoryTest {
    private static Map<String,Object> member(int id, String tier, Integer balance, Integer total) {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("id",id); row.put("full_name","Synthetic Customer " + id); row.put("phone","0900000000");
        row.put("loyalty_level",tier); row.put("loyalty_points",balance); row.put("total_loyalty_points",total);
        row.put("created_at",Timestamp.valueOf("2026-01-02 14:30:00")); return row;
    }
    private static Map<String,Object> entry(int id, int customerId, int points, String type) {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("id",id); row.put("customer_id",customerId); row.put("points",points); row.put("type",type);
        row.put("description","Synthetic ledger entry"); row.put("created_at",Timestamp.valueOf("2026-10-21 12:00:00")); return row;
    }
    private static final class Database {
        final List<Map<String,Object>> members = new ArrayList<>(), history = new ArrayList<>();
        final List<String> queries = new ArrayList<>(); final List<Integer> boundIds = new ArrayList<>();
        final AtomicInteger opens = new AtomicInteger(); boolean failHistory;
        Connection open() {
            opens.incrementAndGet();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                if(m.getName().equals("prepareStatement")) {
                    String sql=(String)a[0]; assertTrue(sql.startsWith("SELECT "),"Directory must not write to the database"); queries.add(sql);
                    var bound=new HashMap<Integer,Integer>();
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(ps,method,args)->{
                        if(method.getName().equals("setInt")) {bound.put((Integer)args[0],(Integer)args[1]);boundIds.add((Integer)args[1]);}
                        if(method.getName().equals("executeQuery")) {
                            boolean ledger=sql.contains("FROM loyalty_histories");
                            if(ledger&&failHistory)throw new SQLException("Synthetic history failure");
                            List<Map<String,Object>> source=ledger?history:members;
                            List<Map<String,Object>> rows=source.stream().filter(row->!sql.contains("WHERE ") || Objects.equals(row.get(ledger?"customer_id":"id"),bound.get(1))).toList();
                            AtomicInteger cursor=new AtomicInteger(-1);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(rs,get,values)->{
                                if(get.getName().equals("next"))return cursor.incrementAndGet()<rows.size();
                                if(get.getName().startsWith("get")&&values!=null) {
                                    String field=(String)values[0];
                                    String selected=sql.substring(7,sql.indexOf(" FROM "));
                                    assertTrue(Arrays.asList(selected.split(", ")).contains(field),"Read only selected columns: "+field);
                                    Object value=rows.get(cursor.get()).get(field);
                                    if(get.getName().equals("getInt"))return value==null?0:((Number)value).intValue();
                                    return value;
                                }
                                return null;
                            });
                        }
                        return null;
                    });
                }
                return null;
            });
        }
    }

    @Test void listsAllMembersCountsStoredTiersAndDoesNotReclassifyOrTruncate() throws Exception {
        Database db=new Database();
        for(int i=1;i<=65;i++)db.members.add(member(i,List.of("Silver","Gold","VIP","Member").get((i-1)%4),42,42));
        var result=new LoyaltyDirectoryService(db::open).directory();
        var rows=(List<?>)result.get("members");assertEquals(65,rows.size());
        assertEquals(Map.of("total",65,"Silver",17,"Gold",16,"VIP",16,"other",16),result.get("summary"));
        var gold=(Map<?,?>)rows.get(1);assertEquals("Gold",gold.get("loyaltyLevel"));assertEquals(42,gold.get("totalLoyaltyPoints"));
        assertEquals("2026-01-02T14:30",gold.get("joinedAt"));
        assertFalse(db.queries.get(0).contains("LIMIT 50"));
        assertTrue(db.queries.get(0).endsWith("ORDER BY created_at DESC, id DESC"));
    }
    @Test void emptyDirectoryAndMissingFieldsDoNotBecomeSampleValues() throws Exception {
        Database db=new Database();var service=new LoyaltyDirectoryService(db::open);
        assertEquals(List.of(),service.directory().get("members"));
        var row=member(1,null,null,null);row.put("created_at",null);db.members.add(row);
        var actual=(Map<?,?>)((List<?>)service.directory().get("members")).get(0);
        assertNull(actual.get("loyaltyLevel"));assertNull(actual.get("loyaltyPoints"));assertNull(actual.get("joinedAt"));
    }
    @Test void readsCanonicalLedgerSignedChangesAndOnlyTheSelectedCustomer() throws Exception {
        Database db=new Database();db.members.add(member(7,"VIP",900,1200));
        db.history.add(entry(3,7,-100,"redeem"));db.history.add(entry(2,7,200,"earn"));db.history.add(entry(1,8,999,"earn"));
        var result=new LoyaltyDirectoryService(db::open).history(7);
        var rows=(List<?>)result.get("history");assertEquals(2,rows.size());
        assertEquals(-100,((Map<?,?>)rows.get(0)).get("pointsChange"));
        assertEquals(200,((Map<?,?>)rows.get(1)).get("pointsChange"));
        assertEquals(List.of(7,7),db.boundIds);
        assertTrue(db.queries.get(1).contains("FROM loyalty_histories WHERE customer_id = ? ORDER BY created_at DESC, id DESC"));
    }
    @Test void distinguishesMissingCustomerEmptyLedgerAndDatabaseFailure() throws Exception {
        Database db=new Database();var service=new LoyaltyDirectoryService(db::open);
        assertNull(service.history(7));assertEquals(1,db.queries.size());
        db.members.add(member(7,"Silver",0,0));assertEquals(List.of(),service.history(7).get("history"));
        db.failHistory=true;assertThrows(SQLException.class,()->service.history(7));
        assertThrows(IllegalArgumentException.class,()->service.history(0));
    }
    @Test void verifiedStaffAndAdminCanReadButHeadersCannotGrantAccessOrWrites() throws Exception {
        System.setProperty("hotel.jwt.secret","synthetic-phase2-jwt-test-fixture-secret-only");
        Database db=new Database();db.members.add(member(7,"Silver",0,0));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var context=server.createContext("/api/v1/loyalty",new LoyaltyDirectoryController(new LoyaltyDirectoryService(db::open)));
        context.getFilters().add(new JwtFilter());server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            for(String role:List.of("staff","front_desk","admin","2"))assertEquals(200,request(base,"/members","GET",role,"guest").statusCode());
            int opened=db.opens.get();
            for(String role:List.of("customer","guest","housekeeping"))assertEquals(403,request(base,"/members/7/history","GET",role,"STAFF").statusCode());
            assertEquals(401,request(base,"/members","GET",null,"STAFF").statusCode());
            assertEquals(405,request(base,"/members","POST","staff","STAFF").statusCode());
            assertEquals(opened,db.opens.get(),"Denied and write requests must not open the database");
            assertEquals(404,request(base,"/members/999/history","GET","staff","guest").statusCode());
            assertEquals(400,request(base,"/members/9999999999999/history","GET","staff","guest").statusCode());
            assertEquals(404,request(base,"/members/7/unknown","GET","staff","guest").statusCode());
            var success=request(base,"/members/7/history","GET","staff","guest");
            assertEquals(0,JsonParser.parseString(success.body()).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("history").size());
            db.failHistory=true;var failed=request(base,"/members/7/history","GET","staff","guest");
            assertEquals(503,failed.statusCode());assertFalse(failed.body().contains("Synthetic history failure"));
        } finally {server.stop(0);}
    }
    private static HttpResponse<String> request(String base,String path,String method,String role,String header)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+"/api/v1/loyalty"+path)).header("X-User-Role",header).method(method,HttpRequest.BodyPublishers.noBody());
        if(role!=null)builder.header("Authorization","Bearer "+JwtTokenUtil.issueToken(11,"synthetic-staff",role));
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
}
