package quanlykhachsan.backend.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RoomImageUrlValidationTest {
    @Test void acceptsManagedMediaUrlsReturnedByTheUploadApi() {
        String path = "/api/v1/media/44baad75-eec8-4eba-a4aa-42c2991bb26a";
        assertEquals(path, RoomController.validateImageUrl(path));
    }

    @Test void continuesToAcceptAbsoluteHttpAndHttpsImages() {
        assertEquals("https://images.example.test/room.jpg",
                RoomController.validateImageUrl("https://images.example.test/room.jpg"));
        assertEquals("http://localhost:3000/room.jpg",
                RoomController.validateImageUrl("http://localhost:3000/room.jpg"));
    }

    @Test void rejectsUnmanagedRelativePathsAndOtherProtocols() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomController.validateImageUrl("/images/room.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> RoomController.validateImageUrl("/api/v1/media/not-a-uuid"));
        assertThrows(IllegalArgumentException.class,
                () -> RoomController.validateImageUrl("javascript:alert(1)"));
    }
}
