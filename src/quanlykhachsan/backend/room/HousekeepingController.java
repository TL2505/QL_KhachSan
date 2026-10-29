package quanlykhachsan.backend.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.PortalValidation;

public final class HousekeepingController implements HttpHandler {
    private static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String role = String.valueOf(exchange.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("housekeeping", "admin").contains(role)) {
            ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Housekeeping role required"));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(exchange.getRequestMethod()) && "/api/v1/housekeeping/tasks".equals(path)) {
            listTasks(exchange);
            return;
        }
        if (path.matches("/api/v1/housekeeping/tasks/[0-9]+/start") && "POST".equals(exchange.getRequestMethod())) {
            if (!"housekeeping".equals(role)) {
                ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Only housekeeping staff can claim a task"));
                return;
            }
            startTask(exchange, taskId(path));
            return;
        }
        if (path.matches("/api/v1/housekeeping/tasks/[0-9]+/complete") && "POST".equals(exchange.getRequestMethod())) {
            if (!"housekeeping".equals(role)) {
                ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Only housekeeping staff can complete a task"));
                return;
            }
            completeTask(exchange, taskId(path));
            return;
        }

        // Retain the administrator's room-state correction route. A housekeeping login
        // must complete an assigned task so room status and task status change together.
        if ("housekeeping".equals(role)) {
            ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Use the assigned housekeeping task to mark a room clean"));
            return;
        }
        updateRoomState(exchange, role);
    }

    private static void listTasks(HttpExchange exchange) throws IOException {
        String sql = "SELECT t.id,t.booking_id,t.room_id,r.room_number,rt.name AS room_type_name,"
                + "t.rental_type,t.estimated_duration_minutes,t.status,t.due_at,t.expected_finish_at,t.created_at,"
                + "t.started_at,t.completed_at,t.assigned_to_user_id,assigned.full_name AS assigned_to_name,"
                + "r.status AS room_status,r.version AS room_version,r.ready_not_before,"
                + "(t.due_at IS NOT NULL AND t.due_at<CURRENT_TIMESTAMP(6)) AS overdue,"
                + "(t.due_at IS NOT NULL AND t.expected_finish_at IS NOT NULL AND t.expected_finish_at>t.due_at) AS turnaround_conflict "
                + "FROM cleaning_tasks t JOIN rooms r ON r.id=t.room_id "
                + "LEFT JOIN room_types rt ON rt.id=r.room_type_id "
                + "LEFT JOIN users assigned ON assigned.id=t.assigned_to_user_id "
                + "WHERE t.status IN ('pending','in_progress') OR (t.status='completed' AND t.completed_at>=CURRENT_DATE AND t.completed_at<DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY)) "
                + "ORDER BY (t.status='completed'),"
                + "(t.due_at IS NOT NULL AND t.expected_finish_at IS NOT NULL AND t.expected_finish_at>t.due_at) DESC,"
                + "CASE t.status WHEN 'in_progress' THEN 0 ELSE 1 END,(t.due_at IS NULL),t.due_at,t.created_at LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
            JsonArray tasks = new JsonArray();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    JsonObject task = new JsonObject();
                    task.addProperty("id", rows.getLong("id"));
                    task.addProperty("booking_id", rows.getInt("booking_id"));
                    task.addProperty("room_id", rows.getInt("room_id"));
                    task.addProperty("room_number", rows.getString("room_number"));
                    task.addProperty("room_type_name", rows.getString("room_type_name"));
                    task.addProperty("rental_type", rows.getString("rental_type"));
                    task.addProperty("estimated_duration_minutes", rows.getInt("estimated_duration_minutes"));
                    task.addProperty("status", rows.getString("status"));
                    addDate(task, rows, "due_at");
                    addDate(task, rows, "expected_finish_at");
                    addDate(task, rows, "created_at");
                    addDate(task, rows, "started_at");
                    addDate(task, rows, "completed_at");
                    int assigneeId = rows.getInt("assigned_to_user_id");
                    if (rows.wasNull()) task.add("assigned_to_user_id", JsonNull.INSTANCE);
                    else task.addProperty("assigned_to_user_id", assigneeId);
                    String assignee = rows.getString("assigned_to_name");
                    if (assignee == null) task.add("assigned_to_name", JsonNull.INSTANCE);
                    else task.addProperty("assigned_to_name", assignee);
                    task.addProperty("room_status", rows.getString("room_status"));
                    task.addProperty("room_version", rows.getInt("room_version"));
                    addDate(task, rows, "ready_not_before");
                    task.addProperty("overdue", rows.getBoolean("overdue"));
                    task.addProperty("turnaround_conflict", rows.getBoolean("turnaround_conflict"));
                    tasks.add(task);
                }
            }
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(tasks));
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Cleaning task list is temporarily unavailable"));
        }
    }

    private static void startTask(HttpExchange exchange, long taskId) throws IOException {
        try {
            int userId = authenticatedUserId(exchange);
            try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String status;
                String roomStatus;
                LocalDateTime readyAt;
                Integer assigneeId;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT t.status,r.status,r.ready_not_before,t.assigned_to_user_id "
                                + "FROM cleaning_tasks t JOIN rooms r ON r.id=t.room_id WHERE t.id=? FOR UPDATE")) {
                    query.setLong(1, taskId);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next()) {
                            connection.rollback();
                            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Cleaning task not found"));
                            return;
                        }
                        status = row.getString(1);
                        roomStatus = row.getString(2);
                        readyAt = row.getObject(3, LocalDateTime.class);
                        int assigned = row.getInt(4);
                        assigneeId = row.wasNull() ? null : assigned;
                    }
                }
                if (!"pending".equals(status) || !"vacant_dirty".equals(roomStatus)
                        || assigneeId != null || (readyAt != null && readyAt.isAfter(LocalDateTime.now(HOTEL_ZONE)))) {
                    connection.rollback();
                    ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Task is already claimed or the room is not ready for cleaning"));
                    return;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE cleaning_tasks SET status='in_progress',assigned_to_user_id=?,started_at=CURRENT_TIMESTAMP(6) "
                                + "WHERE id=? AND status='pending' AND assigned_to_user_id IS NULL")) {
                    update.setInt(1, userId);
                    update.setLong(2, taskId);
                    if (update.executeUpdate() != 1) throw new SQLException("Cleaning task changed during claim");
                }
                connection.commit();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(Map.of(
                        "task_id", taskId, "assigned_to_user_id", userId, "status", "in_progress")));
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
            }
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Cleaning task could not be started"));
        } catch (IllegalArgumentException error) {
            ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required"));
        }
    }

    private static void completeTask(HttpExchange exchange, long taskId) throws IOException {
        JsonObject body;
        Integer expectedVersion;
        try {
            body = PortalValidation.body(exchange);
            expectedVersion = body.has("room_version") ? PortalValidation.integer(body, "room_version", 0) : null;
        } catch (IllegalArgumentException error) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Invalid room version"));
            return;
        }
        try {
            int userId = authenticatedUserId(exchange);
            try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int roomId;
                String taskStatus;
                String roomStatus;
                int roomVersion;
                Integer assigneeId;
                LocalDateTime readyAt;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT t.room_id,t.status,t.assigned_to_user_id,r.status,r.version,r.ready_not_before "
                                + "FROM cleaning_tasks t JOIN rooms r ON r.id=t.room_id WHERE t.id=? FOR UPDATE")) {
                    query.setLong(1, taskId);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next()) {
                            connection.rollback();
                            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Cleaning task not found"));
                            return;
                        }
                        roomId = row.getInt(1);
                        taskStatus = row.getString(2);
                        int assigned = row.getInt(3);
                        assigneeId = row.wasNull() ? null : assigned;
                        roomStatus = row.getString(4);
                        roomVersion = row.getInt(5);
                        readyAt = row.getObject(6, LocalDateTime.class);
                    }
                }
                if (assigneeId == null || assigneeId != userId) {
                    connection.rollback();
                    ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("This task is assigned to another housekeeping employee"));
                    return;
                }
                if (!"in_progress".equals(taskStatus) || !"vacant_dirty".equals(roomStatus)
                        || (readyAt != null && readyAt.isAfter(LocalDateTime.now(HOTEL_ZONE)))) {
                    connection.rollback();
                    ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Task or room status changed; reload the task list"));
                    return;
                }
                if (expectedVersion != null && expectedVersion != roomVersion) {
                    connection.rollback();
                    ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Room state changed; reload the task list"));
                    return;
                }
                var change = new RoomStateService.Change(roomId, roomVersion, "vacant_clean", userId,
                        "housekeeping", "Housekeeping task completed", null, null, null, null, null);
                JsonObject updated = new RoomStateService().change(connection, change);
                connection.commit();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(updated));
            } catch (OptimisticLockException | IllegalStateException error) {
                connection.rollback();
                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Room state changed or the room is not ready to be completed"));
            } catch (SecurityException error) {
                connection.rollback();
                ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Housekeeping role required"));
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
            }
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Cleaning task could not be completed"));
        } catch (IllegalArgumentException error) {
            ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required"));
        }
    }

    private static void updateRoomState(HttpExchange exchange, String role) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().matches("/api/v1/rooms/[0-9]+/housekeeping")) {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Housekeeping endpoint not found"));
                return;
            }
            int roomId = Integer.parseInt(exchange.getRequestURI().getPath().split("/")[4]);
            JsonObject body = PortalValidation.body(exchange);
            String status = PortalValidation.text(body, "status", 30, true);
            if (!Set.of("vacant_clean", "occupied_clean").contains(status)) throw new IllegalArgumentException();
            Integer version = body.has("room_version") ? PortalValidation.integer(body, "room_version", 0) : null;
            Integer seconds = body.has("cleaning_duration_seconds") ? PortalValidation.integer(body, "cleaning_duration_seconds", 0) : null;
            if (seconds != null && seconds > 86400) throw new IllegalArgumentException();
            int userId = authenticatedUserId(exchange);
            var change = new RoomStateService.Change(roomId, version, status, userId, role,
                    "Housekeeping inspection", seconds, PortalValidation.text(body, "inspection_notes", 255, false), null, null, null);
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    JsonObject result = new RoomStateService().change(connection, change);
                    connection.commit();
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(result));
                } catch (Exception error) {
                    connection.rollback();
                    throw error;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SecurityException error) {
            ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Role is not authorized"));
        } catch (IllegalArgumentException error) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Invalid housekeeping data"));
        } catch (OptimisticLockException | IllegalStateException error) {
            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Room state changed or transition is not permitted"));
        } catch (Exception error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Housekeeping update is temporarily unavailable"));
        }
    }

    private static long taskId(String path) {
        String[] segments = path.split("/");
        return Long.parseLong(segments[5]);
    }

    private static int authenticatedUserId(HttpExchange exchange) {
        Object value = exchange.getAttribute("userId");
        if (!(value instanceof Number number) || number.intValue() <= 0) throw new IllegalArgumentException("Missing user identity");
        return number.intValue();
    }

    private static void addDate(JsonObject target, ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        if (value == null) target.add(column, JsonNull.INSTANCE);
        else target.addProperty(column, value.toString());
    }
}
