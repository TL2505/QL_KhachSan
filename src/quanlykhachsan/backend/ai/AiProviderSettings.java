package quanlykhachsan.backend.ai;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import quanlykhachsan.backend.utils.AesGcmEncryptionUtil;

/** Server-only, encrypted configuration. No API serializes the credential object. */
public final class AiProviderSettings {
    private static final byte[] AAD = "aurelia-ai-provider-v1".getBytes(StandardCharsets.UTF_8);
    private static final AiProviderSettings INSTANCE = new AiProviderSettings(Path.of(System.getProperty("hotel.ai.settings.dir", ".data/ai")));
    private final Path directory;
    private final Path settingsFile;
    private final java.util.function.Supplier<AesGcmEncryptionUtil> testCipher;
    record Configuration(String provider, String model, String apiKey, boolean enabled, String knowledge, String workspaceId) {
        Configuration(String provider, String model, String apiKey, boolean enabled, String knowledge) { this(provider, model, apiKey, enabled, knowledge, ""); }
        @Override public String toString() { return "AI configuration [credential redacted]"; }
    }
    public static AiProviderSettings instance() { return INSTANCE; }
    public AiProviderSettings(Path directory) { this(directory, null); }
    AiProviderSettings(Path directory, java.util.function.Supplier<AesGcmEncryptionUtil> cipher) {
        this.directory = directory.toAbsolutePath(); this.settingsFile = this.directory.resolve("provider.enc"); this.testCipher = cipher;
    }
    synchronized Configuration read() throws IOException, GeneralSecurityException {
        if (!Files.exists(settingsFile)) return new Configuration("openai", "", "", false, "");
        if (Files.size(settingsFile) > 65536) throw new IOException("Invalid AI configuration");
        byte[] plain = cipher(false).decrypt(Files.readAllBytes(settingsFile), AAD);
        try {
            JsonObject value = JsonParser.parseString(new String(plain, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Configuration(value.get("provider").getAsString(), value.get("model").getAsString(), value.get("api_key").getAsString(), value.get("enabled").getAsBoolean(), value.get("knowledge").getAsString(), value.has("workspace_id") ? value.get("workspace_id").getAsString() : "");
        } catch (RuntimeException e) { throw new IOException("Invalid AI configuration"); }
        finally { Arrays.fill(plain, (byte) 0); }
    }
    public synchronized Map<String, Object> metadata() throws IOException, GeneralSecurityException {
        Configuration c = read();
        return Map.of("provider", c.provider(), "model", c.model(), "enabled", c.enabled(), "has_key", !c.apiKey().isEmpty(), "knowledge", c.knowledge(), "workspace_id", c.workspaceId(), "project_context", HotelProjectContext.metadata());
    }
    /** Resolve a draft credential for discovery without saving or requiring a model. */
    synchronized Configuration forModels(JsonObject body) throws IOException, GeneralSecurityException {
        String provider = text(body, "provider", 20, false);
        if (!Set.of("openai", "gemini", "claude", "groq").contains(provider)) throw new IllegalArgumentException("Nhà cung cấp không hợp lệ.");
        String key = text(body, "api_key", 512, true);
        Configuration old = null;
        if (key.isEmpty() || (provider.equals("claude") && !body.has("workspace_id"))) old = read();
        if (key.isEmpty() && old != null && provider.equals(old.provider())) key = old.apiKey();
        if (key.length() < 12 || !key.matches("[!-~]+")) throw new IllegalArgumentException("Vui lòng nhập API key cho nhà cung cấp đã chọn.");
        String workspace = "";
        if (provider.equals("claude")) {
            workspace = body.has("workspace_id") ? text(body, "workspace_id", 128, true)
                    : old != null && old.provider().equals(provider) ? old.workspaceId() : "";
            if (!workspace.isEmpty() && !workspace.matches("wrkspc_[A-Za-z0-9]{1,100}")) throw new IllegalArgumentException("Workspace ID không hợp lệ.");
        }
        return new Configuration(provider, "", key, false, "", workspace);
    }
    public synchronized void update(JsonObject body) throws IOException, GeneralSecurityException {
        String provider = text(body, "provider", 20, false);
        if (!Set.of("openai", "gemini", "claude", "groq").contains(provider)) throw new IllegalArgumentException("Nhà cung cấp không hợp lệ.");
        String model = text(body, "model", 100, false);
        String modelPattern = provider.equals("groq") ? "[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}" : "[A-Za-z0-9][A-Za-z0-9._:-]{0,99}";
        if (!model.matches(modelPattern)) throw new IllegalArgumentException("Tên model không hợp lệ.");
        String key = text(body, "api_key", 512, true);
        String knowledge = text(body, "knowledge", 10000, true);
        if (!body.has("enabled") || !body.get("enabled").isJsonPrimitive() || !body.getAsJsonPrimitive("enabled").isBoolean()) throw new IllegalArgumentException("Trạng thái chatbot không hợp lệ.");
        Configuration old = null;
        if (key.isEmpty() || (provider.equals("claude") && !body.has("workspace_id"))) old = read();
        String workspace = "";
        if (provider.equals("claude")) {
            workspace = body.has("workspace_id") ? text(body, "workspace_id", 128, true)
                    : old != null && old.provider().equals("claude") ? old.workspaceId() : "";
            if (!workspace.isEmpty() && !workspace.matches("wrkspc_[A-Za-z0-9]{1,100}")) throw new IllegalArgumentException("Workspace ID không hợp lệ.");
        }
        if (key.isEmpty() && old != null && provider.equals(old.provider())) key = old.apiKey();
        if (key.length() < 12 || !key.matches("[!-~]+")) throw new IllegalArgumentException("Vui lòng nhập API key hợp lệ cho nhà cung cấp đã chọn.");
        write(new Configuration(provider, model, key, body.get("enabled").getAsBoolean(), knowledge, workspace));
    }
    public synchronized void delete() throws IOException { Files.deleteIfExists(settingsFile); }
    private void write(Configuration c) throws IOException, GeneralSecurityException {
        Files.createDirectories(directory); protect(directory, true);
        JsonObject data = new JsonObject();
        data.addProperty("provider", c.provider()); data.addProperty("model", c.model()); data.addProperty("api_key", c.apiKey()); data.addProperty("enabled", c.enabled()); data.addProperty("knowledge", c.knowledge());
        data.addProperty("workspace_id", c.workspaceId());
        byte[] plain = data.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try { encrypted = cipher(true).encrypt(plain, AAD); } finally { Arrays.fill(plain, (byte) 0); }
        Path temporary = Files.createTempFile(directory, "provider-", ".tmp");
        try {
            protect(temporary, false); Files.write(temporary, encrypted);
            try { Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private AesGcmEncryptionUtil cipher(boolean create) throws IOException {
        if (testCipher != null) return testCipher.get();
        if (System.getenv("AES_VAULT_KEY") != null || System.getenv("AES_VAULT_KEY_FILE") != null) return AesGcmEncryptionUtil.fromEnvironment();
        Path keyFile = directory.resolve("master.key");
        if (!Files.exists(keyFile) && create) {
            byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
            try {
                // Create with restricted permissions before writing any secret bytes.
                Files.createFile(keyFile); protect(keyFile, false); Files.write(keyFile, key);
            } finally { Arrays.fill(key, (byte) 0); }
        }
        byte[] key = Files.readAllBytes(keyFile);
        try { return new AesGcmEncryptionUtil(key); } finally { Arrays.fill(key, (byte) 0); }
    }
    private static void protect(Path path, boolean directory) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl == null) throw new IOException("Private AI credential storage unavailable");
            AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            acl.setAcl(List.of(entry));
        }
    }
    private static String text(JsonObject body, String name, int max, boolean optional) {
        if (!body.has(name) && optional) return "";
        if (!body.has(name) || !body.get(name).isJsonPrimitive() || !body.getAsJsonPrimitive(name).isString()) throw new IllegalArgumentException("Thiếu hoặc sai trường cấu hình.");
        String value = body.get(name).getAsString().strip();
        if (value.length() > max || (!optional && value.isBlank())) throw new IllegalArgumentException("Trường cấu hình không hợp lệ.");
        return value;
    }
}
