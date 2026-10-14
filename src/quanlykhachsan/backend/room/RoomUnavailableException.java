package quanlykhachsan.backend.room;

/** Distinguishes unavailable inventory from a stale version of an available room. */
public final class RoomUnavailableException extends OptimisticLockException {
    private static final long serialVersionUID = 1L;
}
