package quanlykhachsan.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import javax.swing.UIManager;
import quanlykhachsan.Quanlykhachsan;
import quanlykhachsan.frontend.api.ApiConfig;
import quanlykhachsan.frontend.api.ApiClient;
import quanlykhachsan.database.DBConnection;

public class DesktopServerConnectionAndThemeTest {

    @Test
    @DisplayName("Verify FlatLaf Dark Luxury Theme Parameters (Button.arc=12, Component.arc=10, TextComponent.arc=10, rowHeight=36, Slate Dark, Panel, Gold)")
    public void testDesktopThemeInitialization() {
        Quanlykhachsan.initializeTheme();

        assertEquals(12, UIManager.getInt("Button.arc"), "Button.arc must be 12");
        assertEquals(10, UIManager.getInt("Component.arc"), "Component.arc must be 10");
        assertEquals(10, UIManager.getInt("TextComponent.arc"), "TextComponent.arc must be 10");
        assertEquals(36, UIManager.getInt("Table.rowHeight"), "Table.rowHeight must be 36");

        Color panelBg = UIManager.getColor("Panel.background");
        assertNotNull(panelBg);
        assertEquals(new Color(0x1E, 0x29, 0x3B), panelBg, "Panel background must be Slate #1E293B");

        Color appBg = UIManager.getColor("Application.background");
        assertNotNull(appBg);
        assertEquals(new Color(0x0F, 0x17, 0x2A), appBg, "Application background must be Slate Dark #0F172A");

        Color goldAccent = UIManager.getColor("Accent.color");
        assertNotNull(goldAccent);
        assertEquals(new Color(0xF5, 0x9E, 0x0B), goldAccent, "Accent color must be Metallic Gold #F59E0B");
    }

    @Test
    @DisplayName("Verify Desktop App Connection Configuration and VM IP Priority (192.168.56.102)")
    public void testDesktopConnectionConfiguration() {
        assertEquals("192.168.56.102", ApiConfig.DEFAULT_VM_IP);
        assertEquals("http://192.168.56.102:8081/api/v1", ApiConfig.DEFAULT_API_BASE_URL);
        assertEquals("jdbc:mysql://192.168.56.102:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC", ApiConfig.DEFAULT_JDBC_URL);

        assertEquals("http://192.168.56.102:8081/api/v1", DBConnection.DEFAULT_API_BASE_URL);
        assertEquals("jdbc:mysql://192.168.56.102:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC", DBConnection.DEFAULT_JDBC_URL);

        String activeApiUrl = ApiConfig.getBaseUrl();
        assertNotNull(activeApiUrl);
        assertTrue(activeApiUrl.contains("8081"), "Base URL must target backend port 8081");

        String activeJdbcUrl = ApiConfig.getJdbcUrl();
        assertNotNull(activeJdbcUrl);
        assertTrue(activeJdbcUrl.contains("hotel_prod_db"), "JDBC URL must target hotel_prod_db");
    }

    @Test
    @DisplayName("Verify Endpoint Path Resolution avoids duplicate /v1/v1")
    public void testEndpointPathResolution() throws Exception {
        java.lang.reflect.Method resolveMethod = ApiClient.class.getDeclaredMethod("resolve", String.class);
        resolveMethod.setAccessible(true);
        ApiClient client = ApiClient.getInstance();

        java.net.URI uriV1Rooms = (java.net.URI) resolveMethod.invoke(client, "/v1/rooms");
        assertFalse(uriV1Rooms.toString().contains("/v1/v1/"), "Resolved URI must never contain duplicate /v1/v1/");
        assertTrue(uriV1Rooms.toString().endsWith("/rooms"), "Resolved URI must end with /rooms");

        java.net.URI uriUsers = (java.net.URI) resolveMethod.invoke(client, "/users");
        assertFalse(uriUsers.toString().contains("/v1/v1/"));
        assertTrue(uriUsers.toString().endsWith("/users"));

        java.net.URI uriRoles = (java.net.URI) resolveMethod.invoke(client, "/roles");
        assertFalse(uriRoles.toString().contains("/v1/v1/"));
        assertTrue(uriRoles.toString().endsWith("/roles"));
    }
}
