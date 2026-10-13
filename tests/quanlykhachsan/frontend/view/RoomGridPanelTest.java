package quanlykhachsan.frontend.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.room.Room;

class RoomGridPanelTest {
    @Test
    void rendersTwoHundredFixedSizeCellsForAllCanonicalStatuses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Room> rooms = new ArrayList<>();
            String[] statuses = {"vacant_clean", "vacant_dirty", "occupied_clean", "occupied_dirty", "out_of_order", "reserved"};
            for (int i = 0; i < 200; i++) rooms.add(new Room(i + 1, Integer.toString(100 + i), 1, 100, statuses[i % statuses.length]));
            RoomGridPanel grid = new RoomGridPanel();
            grid.setRooms(rooms);
            assertEquals(200, grid.visibleRoomCount());
            assertEquals(new Dimension(80, 60), grid.roomCells().get(0).getPreferredSize());
            assertEquals(new Color(0x4CAF50), RoomCellComponent.statusColor("vacant_clean"));
            assertEquals(new Color(0xFF9800), RoomCellComponent.statusColor("vacant_dirty"));
            assertEquals(new Color(0x2196F3), RoomCellComponent.statusColor("occupied_clean"));
            assertEquals(new Color(0xFF5722), RoomCellComponent.statusColor("occupied_dirty"));
            assertEquals(new Color(0xF44336), RoomCellComponent.statusColor("out_of_order"));
            assertEquals(new Color(0x9C27B0), RoomCellComponent.statusColor("reserved"));
            grid.setRoomDetails(1, "Guest A", "BK-1");
            assertEquals("Guest A", grid.roomCells().get(0).getGuestName());
            assertEquals("BK-1", grid.roomCells().get(0).getBookingId());
        });
    }

    @Test
    void filtersByRoomGuestAndBookingAndRestoresAllWhenCleared() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomGridPanel grid = new RoomGridPanel();
            grid.setRooms(List.of(new Room(1, "501", 1, 100, "occupied_clean"),
                new Room(2, "502", 1, 100, "vacant_clean")));
            grid.setRoomDetails(1, "Guest Nguyen", "BKG-77");
            grid.filter("501");
            assertEquals(1, grid.visibleRoomCount());
            grid.filter("nguyen");
            assertEquals(1, grid.visibleRoomCount());
            grid.filter("77");
            assertEquals(1, grid.visibleRoomCount());
            grid.filter("");
            assertEquals(2, grid.visibleRoomCount());
            assertTrue(grid.roomCells().get(0).isVisible());
            assertFalse(grid.roomCells().get(1).getPreferredSize().width < 80);
        });
    }
}
