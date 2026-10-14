package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Global Exception Pipeline & RFC-7807 Error Sanitizer (TASK-A1.3).
 * <p>
 * Catches uncaught runtime exceptions, unwraps causal exception hierarchies,
 * logs full stack traces internally via SLF4J, and converts them into standardized
 * error responses with zero stack trace leakage to clients.
 */
public class GlobalExceptionHandler extends Filter {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public String description() {
        return "Global Exception Handler and Standard Error Formatter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        try {
            chain.doFilter(exchange);
        } catch (Throwable t) {
            handle(exchange, t);
        }
    }

    /**
     * Centralized exception handling logic for both Filter chain and Router dispatching.
     * Recursively traverses exception causes to unwrap domain exceptions (e.g. from service/DAO layers),
     * maps them to appropriate HTTP statuses, and logs unhandled exceptions with full stack traces
     * while returning safe sanitized error envelopes.
     */
    public static void handle(HttpExchange exchange, Throwable throwable) throws IOException {
        if (throwable == null) {
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_SERVER_ERROR", "Internal Server Error: An unexpected error occurred.");
            return;
        }

        // Traverse the cause hierarchy to find known domain exceptions
        Throwable current = throwable;
        Throwable domainException = null;
        Throwable rootCause = throwable;

        while (current != null) {
            rootCause = current;
            if (domainException == null) {
                if (current instanceof IllegalArgumentException
                        || current instanceof SecurityException
                        || current instanceof IllegalStateException
                        || current.getClass().getSimpleName().contains("OptimisticLock")) {
                    domainException = current;
                } else if (current instanceof java.sql.SQLTransactionRollbackException sqlRollbackEx) {
                    domainException = new IllegalStateException("Concurrent transaction deadlock or lock timeout encountered. Please retry the operation.", sqlRollbackEx);
                } else if (current instanceof SQLException sqlEx) {
                    if (sqlEx.getErrorCode() == 1062 || "23000".equals(sqlEx.getSQLState())
                            || (sqlEx.getMessage() != null && sqlEx.getMessage().toLowerCase().contains("duplicate"))) {
                        domainException = new IllegalStateException("Duplicate entry conflict: A record with this unique identifier already exists", sqlEx);
                    } else if (sqlEx.getErrorCode() == 1213 || sqlEx.getErrorCode() == 1205 || "40001".equals(sqlEx.getSQLState())
                            || (sqlEx.getMessage() != null && (sqlEx.getMessage().toLowerCase().contains("deadlock")
                            || sqlEx.getMessage().toLowerCase().contains("lock wait timeout")))) {
                        domainException = new IllegalStateException("Concurrent transaction deadlock or lock timeout encountered. Please retry the operation.", sqlEx);
                    }
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }

        Throwable target = (domainException != null) ? domainException : rootCause;

        if (target instanceof IllegalArgumentException) {
            logger.warn("Bad Request [{} {}]: {}", exchange.getRequestMethod(), exchange.getRequestURI(), target.getMessage());
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", target.getMessage());
        } else if (target instanceof SecurityException) {
            logger.warn("Forbidden Access [{} {}]: {}", exchange.getRequestMethod(), exchange.getRequestURI(), target.getMessage());
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", target.getMessage());
        } else if (target instanceof IllegalStateException || target.getClass().getSimpleName().contains("OptimisticLock")) {
            logger.warn("Conflict State / Optimistic Lock [{} {}]: {}", exchange.getRequestMethod(), exchange.getRequestURI(), target.getMessage());
            ApiResponseUtil.sendError(exchange, 409, "ERR_409_CONFLICT", target.getMessage());
        } else {
            // Unhandled internal server exception: log full stack trace internally for debugging
            logger.error("Internal Server Exception during processing [{} {}]: {}",
                    exchange.getRequestMethod(), exchange.getRequestURI(), throwable.getMessage(), throwable);
            // Strictly do NOT leak stack traces to client; return safe RFC-7807 500 error
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_SERVER_ERROR", "Internal Server Error: An unexpected error occurred.");
        }
    }

    /**
     * Backward-compatible delegation to ApiResponseUtil.sendError.
     */
    public static void sendError(HttpExchange exchange, int statusCode, String errorCode, String message) throws IOException {
        ApiResponseUtil.sendError(exchange, statusCode, errorCode, message);
    }
}
