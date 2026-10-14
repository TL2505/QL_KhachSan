package quanlykhachsan.backend.report;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import java.util.Map;
import com.sun.net.httpserver.*;
import com.google.gson.JsonParser;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
class FinancialReportControllerTest {
    @Test void closedSavedSnapshotIsFinancialStaffOnly()throws Exception {
        Phase2TestDatabase.require();LocalDate business;
        try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT business_date FROM hotel_business_state WHERE id=1")){r.next();business=r.getDate(1).toLocalDate();}
        LocalDate date=business.minusDays(123);BigDecimal amount=new BigDecimal("900.00"),zero=new BigDecimal("0.00");
        var snapshot=new DbrSnapshot(date.toString(),amount,amount,30,200,new BigDecimal("15.00"),new BigDecimal("30.00"),new BigDecimal("4.50"),Map.of("room_rate",amount),amount,amount,zero,zero,zero);
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO daily_business_reports(audit_date,snapshot_json) VALUES (?,?)")){q.setDate(1,java.sql.Date.valueOf(date));q.setString(2,JsonUtil.getGson().toJson(snapshot));q.executeUpdate();}
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var context=server.createContext("/api/v1/reports/financial/dbr",new FinancialReportController());context.getFilters().add(new JwtFilter());server.start();
        try {
            var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/reports/financial/dbr?date=";
            assertEquals(401,get(client,base+date,null).statusCode());
            for(String role:new String[]{"guest","customer","housekeeping","maintenance","f&b"})assertEquals(403,get(client,base+date,JwtTokenUtil.issueToken(1,"fixture",role)).statusCode());
            for(String role:new String[]{"admin","front_desk"}){var response=get(client,base+date,JwtTokenUtil.issueToken(1,"fixture",role));assertEquals(200,response.statusCode(),response.body());assertEquals(JsonUtil.getGson().toJsonTree(snapshot),JsonParser.parseString(response.body()).getAsJsonObject().get("data"));}
            assertEquals(409,get(client,base+business,JwtTokenUtil.issueToken(1,"fixture","front_desk")).statusCode());
        }finally{server.stop(0);try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM daily_business_reports WHERE audit_date=?")){q.setDate(1,java.sql.Date.valueOf(date));q.executeUpdate();}}
    }
    private static HttpResponse<String> get(HttpClient client,String url,String token)throws Exception {var b=HttpRequest.newBuilder(URI.create(url)).GET();if(token!=null)b.header("Authorization","Bearer "+token);return client.send(b.build(),HttpResponse.BodyHandlers.ofString());}
}
