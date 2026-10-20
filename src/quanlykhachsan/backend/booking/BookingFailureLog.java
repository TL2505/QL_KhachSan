package quanlykhachsan.backend.booking;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/** Correlatable diagnostics without SQL text, contact details, identities or credentials. */
public final class BookingFailureLog {
    private BookingFailureLog() { }

    public static String record(String operation, Throwable failure) {
        String reference = UUID.randomUUID().toString();
        StringBuilder line = new StringBuilder("[BookingFailure] reference=").append(reference)
            .append(" operation=").append(operation.replaceAll("[^a-zA-Z0-9/-]", "_"));
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            append(line, cause);
            if (cause instanceof SQLException sql) {
                for (SQLException next = sql.getNextException(); next != null && seen.add(next); next = next.getNextException()) append(line, next);
            }
        }
        System.err.println(line);
        return reference;
    }

    private static void append(StringBuilder line, Throwable cause) {
        line.append(" cause=").append(cause.getClass().getSimpleName());
        if (cause instanceof SQLException sql) {
            String state = sql.getSQLState();
            line.append(" sqlState=").append(state != null && state.matches("[A-Za-z0-9]{5}") ? state : "unknown")
                .append(" vendorCode=").append(sql.getErrorCode());
        }
        for (StackTraceElement frame : cause.getStackTrace()) {
            if (frame.getClassName().startsWith("quanlykhachsan.")) {
                line.append(" at=").append(frame.getClassName()).append('#').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
                break;
            }
        }
        // Exception messages can contain SQL literals, phone numbers and passport values.
    }
}
