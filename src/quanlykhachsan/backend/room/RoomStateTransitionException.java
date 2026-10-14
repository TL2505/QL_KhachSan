package quanlykhachsan.backend.room;

public final class RoomStateTransitionException extends IllegalStateException {
    private static final long serialVersionUID=1L;
    public RoomStateTransitionException(){super("Room transition is not permitted");}
}
