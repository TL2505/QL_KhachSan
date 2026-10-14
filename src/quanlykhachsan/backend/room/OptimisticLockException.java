package quanlykhachsan.backend.room;

/** Indicates that the room state or version changed before reservation. */
public final class OptimisticLockException extends Exception {
    private static final long serialVersionUID = 1L;

    public OptimisticLockException() {
        super("Room state changed by another concurrent transaction");
    }
}
