package quanlykhachsan.frontend.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import quanlykhachsan.backend.room.Room;

/** Five-column scrollable room map; all mutations and filtering run on the EDT. */
public final class RoomGridPanel extends JPanel {
    public static final int COLUMNS = 5;
    public static final int CELL_WIDTH = 80;
    public static final int CELL_HEIGHT = 60;
    private final JPanel cells = new JPanel(new GridLayout(0, COLUMNS, 4, 4));
    private final List<RoomCellComponent> tiles = new ArrayList<>();
    private List<RoomCellComponent> allTiles = Collections.emptyList();
    private Consumer<Room> selectionListener = ignored -> { };
    private Consumer<String> folioListener = ignored -> { };

    public RoomGridPanel() {
        super(new BorderLayout());
        JScrollPane scroll = new JScrollPane(cells);
        cells.setPreferredSize(new Dimension(COLUMNS * (CELL_WIDTH + 4), 0));
        add(scroll, BorderLayout.CENTER);
    }

    public void setSelectionListener(Consumer<Room> listener) { selectionListener = listener; }
    public void setFolioListener(Consumer<String> listener) { folioListener = listener; }

    public void setRooms(List<Room> rooms) {
        requireEdt();
        cells.removeAll();
        tiles.clear();
        if (rooms != null) for (Room room : rooms) {
            RoomCellComponent tile = new RoomCellComponent();
            tile.setRoom(room, "", "");
            tile.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() >= 2) folioListener.accept(tile.getBookingId());
                    else selectionListener.accept(tile.getRoom());
                }
            });
            tiles.add(tile);
            cells.add(tile);
        }
        allTiles = List.copyOf(tiles);
        cells.revalidate();
        cells.repaint();
    }

    public void setRoomDetails(int roomId, String guestName, String bookingId) {
        requireEdt();
        for (RoomCellComponent tile : allTiles) {
            if (tile.getRoom() != null && tile.getRoom().getId() == roomId) {
                tile.setRoom(tile.getRoom(), guestName, bookingId);
                break;
            }
        }
    }

    public void filter(String query) {
        requireEdt();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        cells.removeAll();
        for (RoomCellComponent tile : allTiles) {
            Room room = tile.getRoom();
            boolean match = needle.isEmpty() || room.getRoomNumber().toLowerCase(Locale.ROOT).contains(needle)
                || tile.getGuestName().toLowerCase(Locale.ROOT).contains(needle)
                || tile.getBookingId().toLowerCase(Locale.ROOT).contains(needle);
            tile.setVisible(match);
            if (match) cells.add(tile);
        }
        cells.revalidate();
        cells.repaint();
    }

    public int visibleRoomCount() {
        int visible = 0;
        for (RoomCellComponent tile : allTiles) if (tile.isVisible()) visible++;
        return visible;
    }
    public List<RoomCellComponent> roomCells() { return List.copyOf(allTiles); }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Room grid updates must run on EDT");
    }
}
