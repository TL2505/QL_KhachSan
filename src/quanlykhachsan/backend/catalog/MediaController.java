package quanlykhachsan.backend.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.SecurityUtil;

/** Stores validated image uploads outside the database and serves them by opaque key. */
public final class MediaController implements HttpHandler {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Path MEDIA_ROOT = Path.of(System.getenv().getOrDefault("HOTEL_MEDIA_DIR", ".data/media"))
            .toAbsolutePath().normalize();

    @Override public void handle(HttpExchange e) throws IOException {
        e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        String path = e.getRequestURI().getPath();
        if ("POST".equalsIgnoreCase(e.getRequestMethod()) && path.equals("/api/v1/admin/catalog/media")) {
            upload(e, false, 0);
        } else if ("GET".equalsIgnoreCase(e.getRequestMethod()) && path.matches("/api/v1/media/[0-9a-fA-F-]{36}")) {
            serve(e, path.substring(path.lastIndexOf('/') + 1));
        } else {
            ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Không tìm thấy nội dung ảnh.");
        }
    }

    public void handleAvatar(HttpExchange e, int userId) throws IOException {
        if (userId <= 0) { ApiResponseUtil.sendError(e, 401, "AUTH_REQUIRED", "Vui lòng đăng nhập lại."); return; }
        String method = e.getRequestMethod();
        if ("POST".equalsIgnoreCase(method)) upload(e, true, userId);
        else if ("DELETE".equalsIgnoreCase(method)) clearAvatar(e, userId);
        else ApiResponseUtil.sendError(e, 405, "METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ.");
    }

    private void upload(HttpExchange e, boolean avatar, int accountId) throws IOException {
        if (!avatar && !SecurityUtil.hasPermission(e, 1, 2)) return;
        byte[] body = e.getRequestBody().readNBytes(7_100_001);
        if (body.length > 7_100_000) { ApiResponseUtil.sendError(e, 413, "ERR_413_PAYLOAD_TOO_LARGE", "Ảnh vượt quá dung lượng cho phép (5 MB). "); return; }
        Path stored = null;
        R2ObjectStorage r2 = null;
        String r2ObjectKey = null;
        boolean r2Uploaded = false;
        boolean databaseRecordCreated = false;
        try {
            JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            String fileName = requiredText(json, "fileName", 255);
            String content = requiredText(json, "dataBase64", 7_000_000);
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(content); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Dữ liệu ảnh không hợp lệ."); }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Ảnh vượt quá dung lượng cho phép (5 MB).");
            ImageInfo info = inspect(bytes);
            String key = UUID.randomUUID().toString();
            String extension = info.mime.equals("image/png") ? ".png" : ".jpg";
            String storageKey;
            String url;
            if (isR2Enabled()) {
                r2 = R2ObjectStorage.fromEnvironment();
                r2ObjectKey = key + extension;
                storageKey = R2ObjectStorage.storageKey(key, extension);
                url = r2.publicUrl(key, r2ObjectKey);
                r2Uploaded = true;
                r2.upload(r2ObjectKey, bytes, info.mime);
            } else {
                Files.createDirectories(MEDIA_ROOT);
                Path target = MEDIA_ROOT.resolve(key + extension).normalize();
                if (!target.startsWith(MEDIA_ROOT)) throw new IOException("Invalid media path");
                Files.write(target, bytes);
                stored = target;
                storageKey = key;
                url = "/api/v1/media/" + key;
            }
            int userId = e.getAttribute("userId") instanceof Number n ? n.intValue() : 0;
            try (Connection c = DatabaseConnection.getConnection()) {
                if (avatar) c.setAutoCommit(false);
                try (PreparedStatement q = c.prepareStatement(
                    "INSERT INTO media_assets(storage_key,public_url,mime_type,size_bytes,width,height,original_name,uploaded_by) VALUES(?,?,?,?,?,?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, storageKey); q.setString(2, url); q.setString(3, info.mime); q.setInt(4, bytes.length);
                    q.setInt(5, info.width); q.setInt(6, info.height); q.setString(7, safeFileName(fileName));
                    if (userId > 0) q.setInt(8, userId); else q.setNull(8, java.sql.Types.INTEGER);
                    q.executeUpdate();
                    try (ResultSet rs = q.getGeneratedKeys()) {
                        long id = rs.next() ? rs.getLong(1) : 0L;
                        if (avatar) {
                            try (PreparedStatement update = c.prepareStatement("UPDATE users SET avatar_url=? WHERE id=? AND status='active'")) {
                                update.setString(1, url); update.setInt(2, accountId);
                                if (update.executeUpdate() != 1) throw new IllegalStateException("ACCOUNT_AVATAR_USER_MISSING");
                            }
                        }
                        if (avatar) c.commit();
                        databaseRecordCreated = true;
                        ApiResponseUtil.sendSuccess(e, 201, Map.of("id", id, "url", url, "mime_type", info.mime,
                                "size_bytes", bytes.length, "width", info.width, "height", info.height,
                                "original_name", safeFileName(fileName)));
                    }
                } catch (Exception failure) {
                    if (avatar) try { c.rollback(); } catch (java.sql.SQLException ignored) { }
                    throw failure;
                }
            }
        } catch (IllegalArgumentException | com.google.gson.JsonParseException ex) {
            if (!databaseRecordCreated) cleanupStoredMedia(stored, r2, r2ObjectKey, r2Uploaded);
            ApiResponseUtil.sendError(e, 400, "MEDIA_UPLOAD_INVALID", ex.getMessage());
        } catch (IllegalStateException ex) {
            if (!databaseRecordCreated) cleanupStoredMedia(stored, r2, r2ObjectKey, r2Uploaded);
            if ("ACCOUNT_AVATAR_USER_MISSING".equals(ex.getMessage())) ApiResponseUtil.sendError(e, 403, "ACCOUNT_INACTIVE", "Tài khoản không còn hoạt động.");
            else ApiResponseUtil.sendError(e, 503, "MEDIA_STORAGE_UNAVAILABLE", "Không thể lưu ảnh lúc này.");
        } catch (Exception ex) {
            if (!databaseRecordCreated) cleanupStoredMedia(stored, r2, r2ObjectKey, r2Uploaded);
            ApiResponseUtil.sendError(e, 503, "MEDIA_STORAGE_UNAVAILABLE", "Không thể lưu ảnh lúc này.");
        }
    }

    private void clearAvatar(HttpExchange e, int userId) throws IOException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "UPDATE users SET avatar_url=NULL WHERE id=? AND status='active'")) {
            q.setInt(1, userId);
            if (q.executeUpdate() != 1) { ApiResponseUtil.sendError(e, 403, "ACCOUNT_INACTIVE", "Tài khoản không còn hoạt động."); return; }
            ApiResponseUtil.sendSuccess(e, 200, Map.of("avatarUrl", ""));
        } catch (java.sql.SQLException ex) {
            ApiResponseUtil.sendError(e, 503, "MEDIA_STORAGE_UNAVAILABLE", "Không thể cập nhật ảnh đại diện lúc này.");
        }
    }

    private void serve(HttpExchange e, String key) throws IOException {
        MediaAsset asset;
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT storage_key,mime_type,size_bytes FROM media_assets WHERE storage_key IN (?,?,?)")) {
            q.setString(1, key);
            q.setString(2, "r2:" + key + ".jpg");
            q.setString(3, "r2:" + key + ".png");
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) { ApiResponseUtil.sendError(e, 404, "MEDIA_NOT_FOUND", "Ảnh không tồn tại."); return; }
                asset = new MediaAsset(rs.getString("storage_key"), rs.getString("mime_type"), rs.getLong("size_bytes"));
            }
        } catch (java.sql.SQLException ex) {
            ApiResponseUtil.sendError(e, 503, "MEDIA_STORAGE_UNAVAILABLE", "Không thể tải ảnh lúc này.");
            return;
        }
        try {
            byte[] bytes;
            if (R2ObjectStorage.isR2StorageKey(asset.storageKey())) {
                bytes = R2ObjectStorage.fromEnvironment().download(asset.storageKey(), asset.sizeBytes());
            } else {
                String extension = "image/png".equals(asset.mimeType()) ? ".png" : ".jpg";
                Path file = MEDIA_ROOT.resolve(asset.storageKey() + extension).normalize();
                if (!file.startsWith(MEDIA_ROOT) || !Files.isRegularFile(file)) {
                    ApiResponseUtil.sendError(e, 404, "MEDIA_FILE_MISSING", "Ảnh chưa sẵn sàng hoặc đã bị di chuyển.");
                    return;
                }
                bytes = Files.readAllBytes(file);
                if (bytes.length != asset.sizeBytes()) throw new IOException("Kích thước ảnh không khớp metadata.");
            }
            e.getResponseHeaders().set("Content-Type", asset.mimeType());
            e.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            e.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
            e.sendResponseHeaders(200, bytes.length);
            try (var out = e.getResponseBody()) { out.write(bytes); }
        } catch (Exception ex) {
            ApiResponseUtil.sendError(e, 503, "MEDIA_STORAGE_UNAVAILABLE", "Không thể tải ảnh lúc này.");
        }
    }

    private static boolean isR2Enabled() {
        String provider = System.getenv().getOrDefault("HOTEL_MEDIA_PROVIDER", "local").trim();
        if ("local".equalsIgnoreCase(provider)) return false;
        if ("r2".equalsIgnoreCase(provider)) return true;
        throw new IllegalStateException("HOTEL_MEDIA_PROVIDER chỉ nhận local hoặc r2.");
    }

    private static void cleanupStoredMedia(Path localFile, R2ObjectStorage r2, String objectKey, boolean uploaded) {
        try { if (localFile != null) Files.deleteIfExists(localFile); }
        catch (IOException ignored) { }
        if (uploaded && r2 != null && objectKey != null) {
            try { r2.delete(R2ObjectStorage.storageKey(objectKey.substring(0, objectKey.lastIndexOf('.')),
                    objectKey.substring(objectKey.lastIndexOf('.')))); }
            catch (Exception ignored) { }
        }
    }

    private static ImageInfo inspect(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPEG hoặc PNG hợp lệ.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPEG hoặc PNG hợp lệ.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                String mime = format.equals("png") ? "image/png" : format.equals("jpeg") || format.equals("jpg") ? "image/jpeg" : null;
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (mime == null || width < 1 || height < 1 || width > 6000 || height > 6000 || (long) width * height > 25_000_000L)
                    throw new IllegalArgumentException("Ảnh phải là JPEG/PNG và không vượt quá 6000 × 6000 px.");
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Tệp ảnh bị lỗi.");
                image.flush();
                return new ImageInfo(mime, width, height);
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new IllegalArgumentException("Tệp ảnh bị lỗi."); }
    }

    private static String requiredText(JsonObject json, String key, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Thiếu thông tin ảnh.");
        String value = json.get(key).getAsString();
        if (value.isBlank() || value.length() > max) throw new IllegalArgumentException("Thông tin ảnh không hợp lệ.");
        return value;
    }

    private static String safeFileName(String value) {
        String clean = Path.of(value.replace('\\', '/')).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.length() <= 255 ? clean : clean.substring(clean.length() - 255);
    }

    private record ImageInfo(String mime, int width, int height) { }
    private record MediaAsset(String storageKey, String mimeType, long sizeBytes) { }
}
