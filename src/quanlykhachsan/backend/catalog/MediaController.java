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
            upload(e);
        } else if ("GET".equalsIgnoreCase(e.getRequestMethod()) && path.matches("/api/v1/media/[0-9a-fA-F-]{36}")) {
            serve(e, path.substring(path.lastIndexOf('/') + 1));
        } else {
            ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Không tìm thấy nội dung ảnh.");
        }
    }

    private void upload(HttpExchange e) throws IOException {
        if (!SecurityUtil.hasPermission(e, 1, 2)) return;
        byte[] body = e.getRequestBody().readNBytes(7_100_001);
        if (body.length > 7_100_000) { ApiResponseUtil.sendError(e, 413, "ERR_413_PAYLOAD_TOO_LARGE", "Ảnh vượt quá dung lượng cho phép (5 MB). "); return; }
        Path stored = null;
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
            Files.createDirectories(MEDIA_ROOT);
            Path target = MEDIA_ROOT.resolve(key + extension).normalize();
            if (!target.startsWith(MEDIA_ROOT)) throw new IOException("Invalid media path");
            Files.write(target, bytes);
            stored = target;
            String url = "/api/v1/media/" + key;
            int userId = e.getAttribute("userId") instanceof Number n ? n.intValue() : 0;
            try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                    "INSERT INTO media_assets(storage_key,public_url,mime_type,size_bytes,width,height,original_name,uploaded_by) VALUES(?,?,?,?,?,?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                q.setString(1, key); q.setString(2, url); q.setString(3, info.mime); q.setInt(4, bytes.length);
                q.setInt(5, info.width); q.setInt(6, info.height); q.setString(7, safeFileName(fileName));
                if (userId > 0) q.setInt(8, userId); else q.setNull(8, java.sql.Types.INTEGER);
                q.executeUpdate();
                try (ResultSet rs = q.getGeneratedKeys()) {
                    long id = rs.next() ? rs.getLong(1) : 0L;
                    ApiResponseUtil.sendSuccess(e, 201, Map.of("id", id, "url", url, "mime_type", info.mime,
                            "size_bytes", bytes.length, "width", info.width, "height", info.height,
                            "original_name", safeFileName(fileName)));
                }
            }
        } catch (IllegalArgumentException | com.google.gson.JsonParseException ex) {
            if (stored != null) Files.deleteIfExists(stored);
            ApiResponseUtil.sendError(e, 400, "MEDIA_UPLOAD_INVALID", ex.getMessage());
        } catch (Exception ex) {
            if (stored != null) Files.deleteIfExists(stored);
            ApiResponseUtil.sendError(e, 503, "MEDIA_STORAGE_UNAVAILABLE", "Không thể lưu ảnh lúc này.");
        }
    }

    private void serve(HttpExchange e, String key) throws IOException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT storage_key,mime_type FROM media_assets WHERE storage_key=?")) {
            q.setString(1, key);
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) { ApiResponseUtil.sendError(e, 404, "MEDIA_NOT_FOUND", "Ảnh không tồn tại."); return; }
                String extension = "image/png".equals(rs.getString("mime_type")) ? ".png" : ".jpg";
                Path file = MEDIA_ROOT.resolve(rs.getString("storage_key") + extension).normalize();
                if (!file.startsWith(MEDIA_ROOT) || !Files.isRegularFile(file)) {
                    ApiResponseUtil.sendError(e, 404, "MEDIA_FILE_MISSING", "Ảnh chưa sẵn sàng hoặc đã bị di chuyển."); return;
                }
                byte[] bytes = Files.readAllBytes(file);
                e.getResponseHeaders().set("Content-Type", rs.getString("mime_type"));
                e.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
                e.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
                e.sendResponseHeaders(200, bytes.length);
                try (var out = e.getResponseBody()) { out.write(bytes); }
            }
        } catch (java.sql.SQLException ex) {
            ApiResponseUtil.sendError(e, 503, "MEDIA_STORAGE_UNAVAILABLE", "Không thể tải ảnh lúc này.");
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
}
