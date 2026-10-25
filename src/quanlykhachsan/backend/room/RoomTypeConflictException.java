package quanlykhachsan.backend.room;

public final class RoomTypeConflictException extends RuntimeException {
    public RoomTypeConflictException(String message) { super(message); }
    public RoomTypeConflictException(String message, Throwable cause) { super(message,cause); }
}
