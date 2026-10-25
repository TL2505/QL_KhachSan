package quanlykhachsan.backend.catalog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Small S3-compatible Cloudflare R2 client for room/catalog media. */
final class R2ObjectStorage {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private final URI endpoint;
    private final String bucket;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String publicBaseUrl;
    private final HttpClient httpClient;
    private final Clock clock;

    private R2ObjectStorage(String accountId, String bucket, String accessKeyId,
            String secretAccessKey, String publicBaseUrl, HttpClient httpClient, Clock clock) {
        if (accountId == null || !accountId.matches("[A-Za-z0-9-]{5,64}")) {
            throw new IllegalStateException("R2_ACCOUNT_ID không hợp lệ.");
        }
        if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                || bucket.contains("..") || bucket.contains(".-") || bucket.contains("-.")) {
            throw new IllegalStateException("R2_BUCKET_NAME không hợp lệ.");
        }
        this.endpoint = URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
        this.bucket = bucket;
        this.accessKeyId = required(accessKeyId, "R2_ACCESS_KEY_ID");
        this.secretAccessKey = required(secretAccessKey, "R2_SECRET_ACCESS_KEY");
        try { this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("R2_PUBLIC_BASE_URL không hợp lệ."); }
        this.httpClient = httpClient;
        this.clock = clock;
    }

    static R2ObjectStorage fromEnvironment() {
        return EnvironmentHolder.INSTANCE;
    }

    private static final class EnvironmentHolder {
        private static final R2ObjectStorage INSTANCE = create();

        private static R2ObjectStorage create() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new R2ObjectStorage(System.getenv("R2_ACCOUNT_ID"), System.getenv("R2_BUCKET_NAME"),
                System.getenv("R2_ACCESS_KEY_ID"), System.getenv("R2_SECRET_ACCESS_KEY"),
                System.getenv("R2_PUBLIC_BASE_URL"), client, Clock.systemUTC());
        }
    }

    static R2ObjectStorage forTest(String accountId, String bucket, String accessKeyId,
            String secretAccessKey, String publicBaseUrl, Clock clock) {
        return new R2ObjectStorage(accountId, bucket, accessKeyId, secretAccessKey, publicBaseUrl,
                null, clock);
    }

    static boolean isR2StorageKey(String storageKey) {
        return storageKey != null && storageKey.startsWith("r2:");
    }

    static String objectKeyFromStorageKey(String storageKey) {
        if (storageKey == null || !storageKey.matches("r2:[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw new IllegalArgumentException("R2 storage key không hợp lệ.");
        }
        return storageKey.substring("r2:".length());
    }

    static String storageKey(String mediaId, String extension) {
        if (mediaId == null || !mediaId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Mã ảnh không hợp lệ.");
        }
        if (!".jpg".equals(extension) && !".png".equals(extension)) {
            throw new IllegalArgumentException("Định dạng ảnh R2 không hợp lệ.");
        }
        return "r2:" + mediaId + extension;
    }

    String publicUrl(String mediaId, String objectKey) {
        if (publicBaseUrl == null) return "/api/v1/media/" + mediaId;
        String result = publicBaseUrl + "/" + objectKey;
        if (result.length() > 255) throw new IllegalStateException("R2_PUBLIC_BASE_URL quá dài.");
        return result;
    }

    void upload(String objectKey, byte[] content, String contentType) throws IOException {
        request("PUT", objectKey, content, contentType);
    }

    byte[] download(String storageKey, long expectedSize) throws IOException {
        String objectKey = objectKeyFromStorageKey(storageKey);
        HttpResponse<byte[]> response = request("GET", objectKey, null, null);
        byte[] bytes = response.body();
        if (expectedSize < 1 || bytes.length != expectedSize || bytes.length > 5 * 1024 * 1024) {
            throw new IOException("Kích thước ảnh trên R2 không khớp metadata.");
        }
        return bytes;
    }

    void delete(String storageKey) throws IOException {
        request("DELETE", objectKeyFromStorageKey(storageKey), null, null);
    }

    private HttpResponse<byte[]> request(String method, String objectKey, byte[] content, String contentType)
            throws IOException {
        if (objectKey == null || !objectKey.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw new IllegalArgumentException("R2 object key không hợp lệ.");
        }
        URI uri = endpoint.resolve("/" + bucket + "/" + objectKey);
        byte[] payload = content == null ? new byte[0] : content;
        String payloadHash = sha256Hex(payload);
        Instant now = clock.instant();
        String amzDate = AMZ_DATE.format(now);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash);
        if (contentType != null) builder.header("Content-Type", contentType);
        builder.header("Authorization", authorizationHeader(method, uri, contentType, payloadHash,
                accessKeyId, secretAccessKey, now));
        switch (method) {
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload));
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            default -> throw new IllegalArgumentException("Unsupported R2 method.");
        }
        HttpResponse<byte[]> response;
        try { response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Yêu cầu lưu trữ ảnh bị gián đoạn.", ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Cloudflare R2 trả về HTTP " + response.statusCode() + ".");
        }
        return response;
    }

    static String authorizationHeader(String method, URI uri, String contentType, String payloadHash,
            String accessKeyId, String secretAccessKey, Instant instant) {
        String amzDate = AMZ_DATE.format(instant);
        String dateStamp = DATE_STAMP.format(instant);
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("host", uri.getRawAuthority().toLowerCase(Locale.ROOT));
        if (contentType != null) headers.put("content-type", normalizeHeader(contentType));
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzDate);
        StringBuilder canonicalHeaders = new StringBuilder();
        headers.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value).append('\n'));
        String signedHeaders = String.join(";", headers.keySet());
        String canonicalRequest = method + "\n" + uri.getRawPath() + "\n"
                + (uri.getRawQuery() == null ? "" : uri.getRawQuery()) + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String scope = dateStamp + "/auto/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = hmac(hmac(hmac(hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8),
                dateStamp), "auto"), "s3"), "aws4_request");
        String signature = toHex(hmac(signingKey, stringToSign));
        return "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    static String normalizePublicBaseUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String candidate = value.trim();
        URI uri;
        try { uri = URI.create(candidate); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("R2_PUBLIC_BASE_URL không hợp lệ."); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("R2_PUBLIC_BASE_URL phải là HTTPS và không chứa user, query hoặc fragment.");
        }
        return candidate.replaceAll("/+$", "");
    }

    private static String required(String value, String variable) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Thiếu cấu hình " + variable + ".");
        return value.trim();
    }

    private static String normalizeHeader(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) { throw new IllegalStateException("Không thể ký yêu cầu R2.", ex); }
    }

    private static String sha256Hex(byte[] value) {
        try { return toHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception ex) { throw new IllegalStateException("Không thể băm yêu cầu R2.", ex); }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value));
        return out.toString();
    }
}
