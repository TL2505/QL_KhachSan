package quanlykhachsan.frontend.view;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Optional detached room map with bounds safely restored across monitor changes. */
public final class DetachedRoomMapFrame extends JFrame {
    private static final String CONFIG_DIR = ".ql-khachsan";
    private static final String CONFIG_FILE = "room-map.properties";
    private final Path propertiesPath = Path.of(System.getProperty("user.home"), CONFIG_DIR, CONFIG_FILE);

    public DetachedRoomMapFrame(RoomGridPanel grid) {
        super("Hotel PMS — Room Map");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(grid);
        restoreBounds();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) { saveBounds(); }
        });
    }

    private void restoreBounds() {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        Rectangle fallback = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) { properties.load(input); }
        catch (IOException ignored) { }
        int index = parse(properties, "monitor", devices.length > 1 ? 1 : 0);
        if (devices.length == 0) { setBounds(fallback); return; }
        if (index < 0 || index >= devices.length) index = 0;
        GraphicsConfiguration configuration = devices[index].getDefaultConfiguration();
        Rectangle screen = configuration.getBounds();
        int width = clamp(parse(properties, "width", 640), 320, Math.max(320, screen.width));
        int height = clamp(parse(properties, "height", 480), 240, Math.max(240, screen.height));
        int x = parse(properties, "x", screen.x + Math.max(0, (screen.width - width) / 2));
        int y = parse(properties, "y", screen.y + Math.max(0, (screen.height - height) / 2));
        x = clamp(x, screen.x, screen.x + screen.width - width);
        y = clamp(y, screen.y, screen.y + screen.height - height);
        Rectangle requested = new Rectangle(x, y, width, height);
        setBounds(requested);
    }

    private void saveBounds() {
        Properties properties = new Properties();
        Rectangle bounds = getBounds();
        properties.setProperty("x", Integer.toString(bounds.x));
        properties.setProperty("y", Integer.toString(bounds.y));
        properties.setProperty("width", Integer.toString(bounds.width));
        properties.setProperty("height", Integer.toString(bounds.height));
        int monitor = 0;
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        for (int i = 0; i < devices.length; i++) {
            if (devices[i].getDefaultConfiguration().getBounds().intersects(bounds)) { monitor = i; break; }
        }
        properties.setProperty("monitor", Integer.toString(monitor));
        try {
            Files.createDirectories(propertiesPath.getParent());
            try (OutputStream output = Files.newOutputStream(propertiesPath)) { properties.store(output, "Hotel PMS detached room map"); }
        } catch (IOException ex) {
            System.err.println("Unable to save room-map window bounds: " + ex.getMessage());
        }
    }

    private static int parse(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ex) { return fallback; }
    }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(value, maximum)); }
}
