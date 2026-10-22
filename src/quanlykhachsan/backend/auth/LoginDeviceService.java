package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Browser identification is inventory only and never grants authentication. */
public final class LoginDeviceService {
    private static final String COOKIE = "aurelia_device";
    private LoginDeviceService() { }

    public static void recordLogin(HttpExchange exchange, int userId) throws SQLException {
        record(exchange, userId, true);
    }
    private static String record(HttpExchange exchange, int userId, boolean login) throws SQLException {
        String device = null;
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=",2);
                if (pair.length == 2 && pair[0].equals(COOKIE) && pair[1].matches("[0-9a-f]{64}")) device = pair[1];
            }
        }
        if (device == null) {
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            device = HexFormat.of().formatHex(random);
            boolean secure = exchange instanceof HttpsExchange
                || "https".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"));
            exchange.getResponseHeaders().add("Set-Cookie",COOKIE+"="+device+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=31536000"+(secure?"; Secure":""));
        }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(device.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        String agent = Objects.toString(exchange.getRequestHeaders().getFirst("User-Agent"),"").replaceAll("[\\p{Cntrl}]", "");
        if (agent.length() > 512) agent = agent.substring(0,512);
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement q=c.prepareStatement(
                "INSERT INTO user_login_devices(user_id,device_key_hash,user_agent,last_ip) VALUES(?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE user_agent=VALUES(user_agent),last_ip=VALUES(last_ip),last_seen_at=CURRENT_TIMESTAMP,"
                + "last_login_at=IF(?,CURRENT_TIMESTAMP,last_login_at)")) {
            q.setInt(1,userId); q.setString(2,hash); q.setString(3,agent); q.setString(4,ip); q.setBoolean(5,login); q.executeUpdate();
        }
        return hash;
    }
    public static List<Map<String,Object>> devices(HttpExchange exchange, int userId) throws SQLException {
        // Also covers sessions established before device inventory was deployed.
        String current = record(exchange,userId,false);
        var result = new ArrayList<Map<String,Object>>();
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement q=c.prepareStatement(
                "SELECT id,device_key_hash,user_agent,last_ip,first_login_at,last_login_at,last_seen_at FROM user_login_devices WHERE user_id=? ORDER BY last_seen_at DESC,id DESC LIMIT 50")) {
            q.setInt(1,userId);
            try (ResultSet rows=q.executeQuery()) {
                while(rows.next()) {
                    var item=new LinkedHashMap<String,Object>();
                    item.put("id",rows.getLong("id"));
                    item.put("browser",browser(rows.getString("user_agent")));
                    item.put("platform",platform(rows.getString("user_agent")));
                    item.put("ip",rows.getString("last_ip"));
                    item.put("current",current.equals(rows.getString("device_key_hash")));
                    item.put("firstLoginAt",rows.getTimestamp("first_login_at").toInstant().toString());
                    item.put("lastLoginAt",rows.getTimestamp("last_login_at").toInstant().toString());
                    item.put("lastSeenAt",rows.getTimestamp("last_seen_at").toInstant().toString());
                    result.add(item);
                }
            }
        }
        return result;
    }
    static String browser(String agent) {
        if(agent.contains("Edg/") || agent.contains("EdgA/") || agent.contains("EdgiOS/")) return "Microsoft Edge";
        if(agent.contains("OPR/")) return "Opera";
        if(agent.contains("Chrome/") || agent.contains("CriOS/")) return "Chrome";
        if(agent.contains("Firefox/") || agent.contains("FxiOS/")) return "Firefox";
        if(agent.contains("Safari/")) return "Safari";
        return "Trình duyệt khác";
    }
    static String platform(String agent) {
        if(agent.contains("Android")) return "Android";
        if(agent.contains("iPhone") || agent.contains("iPad")) return "iOS / iPadOS";
        if(agent.contains("Windows")) return "Windows";
        if(agent.contains("Macintosh") || agent.contains("Mac OS X")) return "macOS";
        if(agent.contains("Linux")) return "Linux";
        return "Thiết bị khác";
    }
}
