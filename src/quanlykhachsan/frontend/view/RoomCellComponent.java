package quanlykhachsan.frontend.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import quanlykhachsan.backend.room.Room;

/** Fixed-size, status-colored tile used by the staff room map. */
public final class RoomCellComponent extends JPanel {
    private static final int CELL_WIDTH = 80;
    private static final int CELL_HEIGHT = 60;
    private final JLabel number = new JLabel();
    private final JLabel guest = new JLabel();
    private Room room;
    private String guestName;
    private String bookingId;

    public RoomCellComponent() {
        super(new BorderLayout(0, 0));
        setPreferredSize(new Dimension(CELL_WIDTH, CELL_HEIGHT));
        setMinimumSize(new Dimension(CELL_WIDTH, CELL_HEIGHT));
        setMaximumSize(new Dimension(CELL_WIDTH, CELL_HEIGHT));
        setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        number.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        guest.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        add(number, BorderLayout.NORTH);
        add(guest, BorderLayout.SOUTH);
        setOpaque(true);
    }

    public void setRoom(Room room, String guestName, String bookingId) {
        this.room = room;
        this.guestName = guestName == null ? "" : guestName;
        this.bookingId = bookingId == null ? "" : bookingId;
        number.setText(room == null ? "" : room.getRoomNumber());
        boolean occupied = room != null && "occupied_clean".equals(room.getStatus());
        guest.setText(occupied ? this.guestName : "");
        Color background = statusColor(room == null ? "" : room.getStatus());
        setBackground(background);
        number.setForeground(contrast(background));
        guest.setForeground(contrast(background));
        setToolTipText(room == null ? null : room.getRoomNumber() + " — " + room.getStatus());
        setVisible(room != null);
    }

    public Room getRoom() { return room; }
    public String getGuestName() { return guestName; }
    public String getBookingId() { return bookingId; }

    public static Color statusColor(String status) {
        if (status == null) return Color.GRAY;
        switch (status) {
            case "vacant_clean": return new Color(0x4CAF50);
            case "vacant_dirty": return new Color(0xFF9800);
            case "occupied_clean": return new Color(0x2196F3);
            case "occupied_dirty": return new Color(0xFF5722);
            case "out_of_order": return new Color(0xF44336);
            case "reserved": return new Color(0x9C27B0);
            default: return Color.GRAY;
        }
    }

    private static Color contrast(Color color) {
        return (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) / 1000 >= 150
            ? Color.BLACK : Color.WHITE;
    }
}
