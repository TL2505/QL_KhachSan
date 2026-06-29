package quanlykhachsan.backend.utils;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class JwtUtil {
    
    // Khóa bí mật (Secret Key) để ký JWT. Trong thực tế nên để ở file config.properties
    private static final String SECRET_KEY = "QuanLyKhachSan_SecretKey_VerySecure_2026_!@#$";
    private static final long EXPIRATION_TIME = 86400000L; // 24 giờ (milliseconds)
    
    // Header chuẩn cố định của JWT
    private static final String HEADER_B64 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"; // {"alg":"HS256","typ":"JWT"}

    /**
     * Tạo JWT Token từ thông tin User
     */
    public static String generateToken(int userId, String role, Integer customerId) {
        try {
            long nowMillis = System.currentTimeMillis();
            long expMillis = nowMillis + EXPIRATION_TIME;

            // Tạo Payload
            JsonObject payloadObj = new JsonObject();
            payloadObj.addProperty("userId", userId);
            payloadObj.addProperty("role", role);
            if (customerId != null) {
                payloadObj.addProperty("customerId", customerId);
            }
            payloadObj.addProperty("iat", nowMillis / 1000); // Issued At (giây)
            payloadObj.addProperty("exp", expMillis / 1000); // Expiration (giây)

            Gson gson = JsonUtil.getGson();
            String payloadJson = gson.toJson(payloadObj);
            
            // Encode Base64URL (bỏ dấu =, thay + thành -, / thành _)
            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            
            String dataToSign = HEADER_B64 + "." + payloadB64;
            String signatureB64 = hmacSha256(dataToSign, SECRET_KEY);
            
            return dataToSign + "." + signatureB64;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Giải mã và xác thực Token. Trả về Payload JsonObject nếu hợp lệ, ngược lại trả về null.
     */
    public static JsonObject verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null; // Sai định dạng JWT
            
            String header = parts[0];
            String payload = parts[1];
            String signature = parts[2];
            
            // Tính toán lại chữ ký
            String expectedSignature = hmacSha256(header + "." + payload, SECRET_KEY);
            
            // So sánh chữ ký
            if (!signature.equals(expectedSignature)) {
                return null; // Chữ ký không khớp (Token bị giả mạo)
            }
            
            // Giải mã payload
            String decodedPayload = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonObject payloadObj = JsonUtil.getGson().fromJson(decodedPayload, JsonObject.class);
            
            // Kiểm tra hạn sử dụng (exp)
            if (payloadObj.has("exp")) {
                long exp = payloadObj.get("exp").getAsLong() * 1000; // Đổi ra mili-giây
                if (System.currentTimeMillis() > exp) {
                    return null; // Token đã hết hạn
                }
            }
            
            return payloadObj;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Hàm sinh chữ ký HMAC-SHA256
     */
    private static String hmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
