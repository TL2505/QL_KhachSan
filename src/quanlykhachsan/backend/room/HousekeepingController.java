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
import java.sql.Types;
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
        if ("GET".equals(exchange.getRequestMethod()) && "/api/v1/housekeeping/metrics".equals(path)) {
            metrics(exchange);
            return;
        }
        if (path.matches("/api/v1/housekeeping/tasks/[0-9]+/details") && "GET".equals(exchange.getRequestMethod())) {
            taskDetails(exchange, taskId(path));
            return;
        }
        if (path.matches("/api/v1/housekeeping/tasks/[0-9]+/checklist") && "POST".equals(exchange.getRequestMethod())) {
            if (!"housekeeping".equals(role)) { ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Only housekeeping staff can update a checklist")); return; }
            updateChecklist(exchange, taskId(path));
            return;
        }
        if (path.matches("/api/v1/housekeeping/tasks/[0-9]+/incidents") && "POST".equals(exchange.getRequestMethod())) {
            if (!"housekeeping".equals(role)) { ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Only housekeeping staff can report an incident")); return; }
            reportIncident(exchange, taskId(path));
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
                + "t.needs_reinspection,"
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
                    task.addProperty("needs_reinspection", rows.getBoolean("needs_reinspection"));
                    String roomNumber = rows.getString("room_number");
                    String numeric = roomNumber == null ? "" : roomNumber.replaceAll("\\D", "");
                    task.addProperty("floor", numeric.isEmpty() ? "Chưa rõ tầng" : "Tầng " + numeric.substring(0, Math.max(1, numeric.length() - 2)));
                    String area = roomNumber == null || !roomNumber.matches(".*[A-Za-z].*") ? "Khu chung" : roomNumber.replaceAll("[0-9\\- _]", "").toUpperCase(java.util.Locale.ROOT);
                    task.addProperty("area", area.isBlank() ? "Khu chung" : "Khu " + area);
                    tasks.add(task);
                }
            }
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(tasks));
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Cleaning task list is temporarily unavailable"));
        }
    }

    private static void metrics(HttpExchange exchange) throws IOException {
        String sql = "SELECT SUM(CASE WHEN status='completed' AND completed_at>=CURRENT_DATE AND completed_at<DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY) THEN 1 ELSE 0 END),"
                + "AVG(CASE WHEN status='completed' AND started_at IS NOT NULL AND completed_at>=CURRENT_DATE AND completed_at<DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY) THEN TIMESTAMPDIFF(SECOND,started_at,completed_at) END),"
                + "SUM(CASE WHEN needs_reinspection=1 AND status<>'cancelled' THEN 1 ELSE 0 END) FROM cleaning_tasks";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(sql); ResultSet r = q.executeQuery()) {
            JsonObject data = new JsonObject();
            if (r.next()) {
                data.addProperty("cleaned_today", r.getInt(1));
                double average = r.getDouble(2);
                if (r.wasNull()) data.add("average_duration_seconds", JsonNull.INSTANCE);
                else data.addProperty("average_duration_seconds", Math.round(average));
                data.addProperty("needs_reinspection", r.getInt(3));
            }
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
        } catch (SQLException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Housekeeping metrics are temporarily unavailable"));
        }
    }

    private static void taskDetails(HttpExchange exchange, long taskId) throws IOException {
        try (Connection c = DatabaseConnection.getConnection()) {
            if (!taskExists(c, taskId)) { ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Cleaning task not found")); return; }
            ensureChecklist(c, taskId);
            JsonObject data = new JsonObject();
            JsonArray checklist = new JsonArray();
            try (PreparedStatement q = c.prepareStatement("SELECT item_key,item_label,is_required,completed_by_user_id,completed_at FROM housekeeping_checklist_items WHERE task_id=? ORDER BY item_key")) {
                q.setLong(1, taskId);
                try (ResultSet r = q.executeQuery()) { while (r.next()) {
                    JsonObject item = new JsonObject(); item.addProperty("key", r.getString(1)); item.addProperty("label", r.getString(2));
                    item.addProperty("required", r.getBoolean(3)); int uid = r.getInt(4); if (r.wasNull()) item.add("completed_by_user_id", JsonNull.INSTANCE); else item.addProperty("completed_by_user_id", uid);
                    addDate(item, r, "completed_at"); checklist.add(item);
                } }
            }
            JsonArray incidents = new JsonArray();
            try (PreparedStatement q = c.prepareStatement("SELECT i.id,i.incident_type,i.description,i.photo_url,i.needs_reinspection,i.status,i.created_at,u.full_name FROM housekeeping_incidents i JOIN users u ON u.id=i.reported_by_user_id WHERE i.task_id=? ORDER BY i.created_at DESC LIMIT 30")) {
                q.setLong(1, taskId);
                try (ResultSet r = q.executeQuery()) { while (r.next()) {
                    JsonObject incident = new JsonObject(); incident.addProperty("id", r.getLong(1)); incident.addProperty("type", r.getString(2));
                    incident.addProperty("description", r.getString(3)); if (r.getString(4) == null) incident.add("photo_url", JsonNull.INSTANCE); else incident.addProperty("photo_url", r.getString(4));
                    incident.addProperty("needs_reinspection", r.getBoolean(5)); incident.addProperty("status", r.getString(6)); addDate(incident, r, "created_at"); incident.addProperty("reported_by", r.getString(8)); incidents.add(incident);
                } }
            }
            JsonArray events = new JsonArray();
            try (PreparedStatement q = c.prepareStatement("SELECT e.event_type,e.details,e.created_at,u.full_name FROM housekeeping_task_events e LEFT JOIN users u ON u.id=e.actor_user_id WHERE e.task_id=? ORDER BY e.created_at DESC LIMIT 50")) {
                q.setLong(1, taskId);
                try (ResultSet r = q.executeQuery()) { while (r.next()) {
                    JsonObject item = new JsonObject(); item.addProperty("type", r.getString(1)); if (r.getString(2) == null) item.add("details", JsonNull.INSTANCE); else item.addProperty("details", r.getString(2));
                    addDate(item, r, "created_at"); if (r.getString(4) == null) item.addProperty("actor", "Hệ thống"); else item.addProperty("actor", r.getString(4)); events.add(item);
                } }
            }
            data.add("checklist", checklist); data.add("incidents", incidents); data.add("events", events);
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
        } catch (SQLException ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Task details are temporarily unavailable"));
        }
    }

    private static void updateChecklist(HttpExchange exchange, long taskId) throws IOException {
        JsonObject body; String key; boolean checked;
        try { body = PortalValidation.body(exchange); key = PortalValidation.text(body, "item_key", 40, true); checked = body.has("checked") && body.get("checked").getAsBoolean(); }
        catch (Exception ex) { ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Invalid checklist item")); return; }
        int userId;
        try { userId = authenticatedUserId(exchange); } catch (IllegalArgumentException ex) { ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required")); return; }
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureAssignedTask(c, taskId, userId); ensureChecklist(c, taskId);
                try (PreparedStatement q = c.prepareStatement("UPDATE housekeeping_checklist_items SET completed_by_user_id=?,completed_at=CASE WHEN ? THEN CURRENT_TIMESTAMP(6) ELSE NULL END WHERE task_id=? AND item_key=?")) {
                    if (checked) q.setInt(1, userId); else q.setNull(1, Types.INTEGER); q.setBoolean(2, checked); q.setLong(3, taskId); q.setString(4, key);
                    if (q.executeUpdate() != 1) { c.rollback(); ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Checklist item not found")); return; }
                }
                insertEvent(c, taskId, userId, checked ? "checklist_checked" : "checklist_reopened", checklistLabel(key));
                c.commit(); ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(Map.of("task_id", taskId, "item_key", key, "checked", checked)));
            } catch (SecurityException ex) { c.rollback(); ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error(ex.getMessage())); }
            catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Checklist could not be updated")); }
    }

    private static void reportIncident(HttpExchange exchange, long taskId) throws IOException {
        JsonObject body; String type; String description; String photo; boolean reinspection;
        try {
            body = PortalValidation.body(exchange); type = PortalValidation.text(body, "incident_type", 30, true);
            description = PortalValidation.text(body, "description", 1000, true); photo = PortalValidation.text(body, "photo_url", 500, false);
            reinspection = body.has("needs_reinspection") && body.get("needs_reinspection").getAsBoolean();
            if (!Set.of("damage", "missing_item", "maintenance", "cleanliness", "other").contains(type)) throw new IllegalArgumentException();
            if (photo != null && !photo.isBlank() && !photo.matches("(?:/api/v1/media/[0-9a-fA-F-]{36}|https://[^\\s]{1,450})")) throw new IllegalArgumentException();
        } catch (Exception ex) { ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thông tin sự cố chưa hợp lệ.")); return; }
        int userId;
        try { userId = authenticatedUserId(exchange); } catch (IllegalArgumentException ex) { ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required")); return; }
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int roomId; int bookingId;
                try (PreparedStatement q = c.prepareStatement("SELECT room_id,booking_id FROM cleaning_tasks WHERE id=? AND status='in_progress' AND assigned_to_user_id=? FOR UPDATE")) {
                    q.setLong(1, taskId); q.setInt(2, userId); try (ResultSet r = q.executeQuery()) { if (!r.next()) throw new SecurityException("Chỉ người đang nhận việc mới có thể báo sự cố."); roomId = r.getInt(1); bookingId = r.getInt(2); }
                }
                long incidentId;
                try (PreparedStatement q = c.prepareStatement("INSERT INTO housekeeping_incidents(task_id,room_id,reported_by_user_id,incident_type,description,photo_url,needs_reinspection) VALUES(?,?,?,?,?,?,?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    q.setLong(1, taskId); q.setInt(2, roomId); q.setInt(3, userId); q.setString(4, type); q.setString(5, description.trim());
                    if (photo == null || photo.isBlank()) q.setNull(6, Types.VARCHAR); else q.setString(6, photo); q.setBoolean(7, reinspection); q.executeUpdate();
                    try (ResultSet keys = q.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Incident ID was not returned"); incidentId = keys.getLong(1); }
                }
                if (reinspection) try (PreparedStatement q = c.prepareStatement("UPDATE cleaning_tasks SET needs_reinspection=1 WHERE id=?")) { q.setLong(1, taskId); q.executeUpdate(); }
                insertEvent(c, taskId, userId, "incident_reported", type + ": " + description.trim());
                String roomNumber;
                try (PreparedStatement q = c.prepareStatement("SELECT room_number FROM rooms WHERE id=?")) { q.setInt(1, roomId); try (ResultSet r = q.executeQuery()) { roomNumber = r.next() ? r.getString(1) : "—"; } }
                String title = "Sự cố phòng " + roomNumber + " cần xử lý";
                try (PreparedStatement q = c.prepareStatement("INSERT INTO staff_notifications(event_key,booking_id,title,message,action_url) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE event_key=event_key")) {
                    q.setString(1, "housekeeping-incident:" + incidentId); if (bookingId > 0) q.setInt(2, bookingId); else q.setNull(2, Types.INTEGER);
                    q.setString(3, title); q.setString(4, description.trim()); q.setString(5, "portal.html?view=rooms"); q.executeUpdate();
                }
                c.commit(); ApiResponseUtil.write(exchange, 201, ApiResponseUtil.successWithData(Map.of("incident_id", incidentId, "task_id", taskId, "needs_reinspection", reinspection)));
            } catch (SecurityException ex) { c.rollback(); ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error(ex.getMessage())); }
            catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể lưu báo cáo sự cố lúc này.")); }
    }

    private static boolean taskExists(Connection c, long taskId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM cleaning_tasks WHERE id=?")) { q.setLong(1, taskId); try (ResultSet r = q.executeQuery()) { return r.next(); } }
    }

    private static void ensureAssignedTask(Connection c, long taskId, int userId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM cleaning_tasks WHERE id=? AND status='in_progress' AND assigned_to_user_id=? FOR UPDATE")) {
            q.setLong(1, taskId); q.setInt(2, userId); try (ResultSet r = q.executeQuery()) { if (!r.next()) throw new SecurityException("Chỉ người đang nhận việc mới có thể cập nhật checklist."); }
        }
    }

    private static void ensureChecklist(Connection c, long taskId) throws SQLException {
        String[][] items = {{"linens", "Thay ga giường và vỏ gối"}, {"bathroom", "Vệ sinh phòng tắm"}, {"towels", "Bổ sung khăn và vật dụng"}, {"trash", "Đổ rác và thay túi rác"}, {"equipment", "Kiểm tra thiết bị và đèn"}, {"final_check", "Kiểm tra tổng thể trước bàn giao"}};
        try (PreparedStatement q = c.prepareStatement("INSERT IGNORE INTO housekeeping_checklist_items(task_id,item_key,item_label,is_required) VALUES(?,?,?,1)")) {
            for (String[] item : items) { q.setLong(1, taskId); q.setString(2, item[0]); q.setString(3, item[1]); q.addBatch(); } q.executeBatch();
        }
    }

    private static String checklistLabel(String key) {
        return switch (key) { case "linens" -> "Thay ga giường và vỏ gối"; case "bathroom" -> "Vệ sinh phòng tắm"; case "towels" -> "Bổ sung khăn và vật dụng"; case "trash" -> "Đổ rác và thay túi rác"; case "equipment" -> "Kiểm tra thiết bị và đèn"; case "final_check" -> "Kiểm tra tổng thể trước bàn giao"; default -> key; };
    }

    private static void insertEvent(Connection c, long taskId, int userId, String type, String details) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("INSERT INTO housekeeping_task_events(task_id,actor_user_id,event_type,details) VALUES(?,?,?,?)")) {
            q.setLong(1, taskId); q.setInt(2, userId); q.setString(3, type); q.setString(4, details.length() > 1000 ? details.substring(0, 1000) : details); q.executeUpdate();
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
                ensureChecklist(connection, taskId);
                insertEvent(connection, taskId, userId, "task_started", "Nhận dọn phòng");
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
                ensureChecklist(connection, taskId);
                try (PreparedStatement checklist = connection.prepareStatement(
                        "SELECT COUNT(*) FROM housekeeping_checklist_items WHERE task_id=? AND is_required=1 AND completed_at IS NULL")) {
                    checklist.setLong(1, taskId);
                    try (ResultSet row = checklist.executeQuery()) {
                        row.next();
                        if (row.getInt(1) > 0) {
                            connection.rollback();
                            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Hãy hoàn thành toàn bộ checklist trước khi xác nhận phòng sạch"));
                            return;
                        }
                    }
                }
                var change = new RoomStateService.Change(roomId, roomVersion, "vacant_clean", userId,
                        "housekeeping", "Housekeeping task completed", null, null, null, null, null);
                JsonObject updated = new RoomStateService().change(connection, change);
                try (PreparedStatement update = connection.prepareStatement("UPDATE cleaning_tasks SET needs_reinspection=0 WHERE id=?")) { update.setLong(1, taskId); update.executeUpdate(); }
                insertEvent(connection, taskId, userId, "task_completed", "Hoàn tất checklist và xác nhận phòng sạch");
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
