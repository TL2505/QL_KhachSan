package quanlykhachsan.backend.utils;

/**
 * Thrown when an optimistic locking version conflict is detected during entity updates (TASK-B2.1).
 * <p>
 * Extends {@link IllegalStateException} so that it naturally maps to HTTP 409 Conflict
 * within the {@link GlobalExceptionHandler} pipeline while preserving semantic exception hierarchy.
 */
public class OptimisticLockException extends IllegalStateException {

    public OptimisticLockException(String message) {
        super(message);
    }

    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
