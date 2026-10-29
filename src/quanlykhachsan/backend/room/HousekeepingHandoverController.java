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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.PortalValidation;

/** Creates, accepts, and cancels a transfer of open housekeeping tasks between shifts. */
public final class HousekeepingHandoverController implements HttpHandler {
    private static final Pattern ACTION_PATH = Pattern.compile("/api/v1/housekeeping/handovers/([0-9]+)/(accept|cancel)");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String role = String.valueOf(exchange.getAttribute("role")).toLowerCase(Locale.ROOT);
        if (!"housekeeping".equals(role)) {
            ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Housekeeping role required"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(exchange.getRequestMethod()) && "/api/v1/housekeeping/handovers".equals(path)) {
            list(exchange);
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/v1/housekeeping/handovers".equals(path)) {
            create(exchange);
            return;
        }
        Matcher action = ACTION_PATH.matcher(path);
        if ("POST".equals(exchange.getRequestMethod()) && action.matches()) {
            long id;
            try { id = Long.parseLong(action.group(1)); }
            catch (NumberFormatException error) { ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Invalid handover ID")); return; }
            if ("accept".equals(action.group(2))) accept(exchange, id);
            else cancel(exchange, id);
            return;
        }
        ApiResponseUtil.write(exchange, 405, ApiResponseUtil.error("Method or housekeeping handover route is not supported"));
    }

    private static void list(HttpExchange exchange) throws IOException {
        int userId;
        try { userId = authenticatedUserId(exchange); }
        catch (IllegalArgumentException error) { ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required")); return; }
        String sql = "SELECT h.id,h.outgoing_user_id,outgoing.full_name,h.accepted_by_user_id,accepted.full_name,"
                + "h.status,h.notes,h.created_at,h.accepted_at,i.task_id,i.room_number_at_handover,"
                + "i.room_type_at_handover,i.task_status_at_handover,t.status AS current_task_status "
                + "FROM housekeeping_shift_handovers h "
                + "JOIN users outgoing ON outgoing.id=h.outgoing_user_id "
                + "LEFT JOIN users accepted ON accepted.id=h.accepted_by_user_id "
                + "LEFT JOIN housekeeping_shift_handover_tasks i ON i.handover_id=h.id "
                + "LEFT JOIN cleaning_tasks t ON t.id=i.task_id "
                + "WHERE h.status='pending' OR ((h.outgoing_user_id=? OR h.accepted_by_user_id=?) "
                + "AND h.created_at>=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 30 DAY)) "
                + "ORDER BY (h.status='pending') DESC,h.created_at DESC,h.id DESC,i.task_id LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
            query.setInt(1, userId); query.setInt(2, userId);
            LinkedHashMap<Long, JsonObject> handovers = new LinkedHashMap<>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    long id = rows.getLong("id");
                    JsonObject handover = handovers.get(id);
                    if (handover == null) {
                        handover = new JsonObject();
                        handover.addProperty("id", id);
                        handover.addProperty("outgoing_user_id", rows.getInt("outgoing_user_id"));
                        handover.addProperty("outgoing_name", rows.getString(3));
                        int receiverId = rows.getInt("accepted_by_user_id");
                        if (rows.wasNull()) handover.add("accepted_by_user_id", JsonNull.INSTANCE);
                        else handover.addProperty("accepted_by_user_id", receiverId);
                        String receiverName = rows.getString(5);
                        if (receiverName == null) handover.add("accepted_by_name", JsonNull.INSTANCE);
                        else handover.addProperty("accepted_by_name", receiverName);
                        handover.addProperty("status", rows.getString("status"));
                        handover.addProperty("notes", rows.getString("notes"));
                        addDate(handover, rows, "created_at"); addDate(handover, rows, "accepted_at");
                        handover.add("items", new JsonArray());
                        handovers.put(id, handover);
                    }
                    long taskId = rows.getLong("task_id");
                    if (!rows.wasNull()) {
                        JsonObject item = new JsonObject();
                        item.addProperty("task_id", taskId);
                        item.addProperty("room_number", rows.getString("room_number_at_handover"));
                        String type = rows.getString("room_type_at_handover");
                        if (type == null) item.add("room_type", JsonNull.INSTANCE); else item.addProperty("room_type", type);
                        item.addProperty("status_at_handover", rows.getString("task_status_at_handover"));
                        String currentStatus = rows.getString("current_task_status");
                        if (currentStatus == null) item.add("current_status", JsonNull.INSTANCE); else item.addProperty("current_status", currentStatus);
                        handover.getAsJsonArray("items").add(item);
                    }
                }
            }
            JsonArray data = new JsonArray(); handovers.values().forEach(data::add);
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Housekeeping handovers are temporarily unavailable"));
        }
    }

    private static void create(HttpExchange exchange) throws IOException {
        JsonObject body;
        List<Long> taskIds;
        String notes;
        try {
            body = PortalValidation.body(exchange);
            taskIds = taskIds(body);
            notes = PortalValidation.text(body, "notes", 1000, false);
            if (notes == null) notes = "";
            notes = notes.trim();
            if (taskIds.isEmpty() || notes.isBlank()) throw new IllegalArgumentException();
        } catch (Exception error) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Chọn ít nhất một công việc và nhập ghi chú bàn giao."));
            return;
        }
        int userId;
        try { userId = authenticatedUserId(exchange); }
        catch (IllegalArgumentException error) { ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required")); return; }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<HandoverTask> tasks = new ArrayList<>();
                for (long taskId : taskIds) {
                    HandoverTask task = loadTaskForOutgoing(connection, taskId, userId);
                    if (task == null) throw new HandoverConflict("Chỉ có thể bàn giao công việc đang hoạt động mà bạn đang nhận.");
                    if (hasPendingHandover(connection, taskId)) throw new HandoverConflict("Một công việc đã nằm trong bàn giao đang chờ; hãy tải lại danh sách.");
                    tasks.add(task);
                }
                long handoverId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO housekeeping_shift_handovers(outgoing_user_id,notes) VALUES(?,?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    insert.setInt(1, userId); insert.setString(2, notes); insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Handover ID was not returned"); handoverId = keys.getLong(1); }
                }
                for (HandoverTask task : tasks) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO housekeeping_shift_handover_tasks(handover_id,task_id,room_number_at_handover,room_type_at_handover,task_status_at_handover) VALUES(?,?,?,?,?)")) {
                        insert.setLong(1, handoverId); insert.setLong(2, task.id()); insert.setString(3, task.roomNumber());
                        if (task.roomType() == null) insert.setNull(4, Types.VARCHAR); else insert.setString(4, task.roomType());
                        insert.setString(5, task.status()); insert.executeUpdate();
                    }
                    insertTaskEvent(connection, task.id(), userId, "shift_handover_created", "Đưa vào phiếu bàn giao ca #" + handoverId);
                }
                connection.commit();
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.successWithData(Map.of("handover_id", handoverId, "task_count", tasks.size(), "status", "pending")));
            } catch (HandoverConflict error) {
                connection.rollback(); ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(error.getMessage()));
            } catch (SQLException error) {
                connection.rollback(); throw error;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể tạo phiếu bàn giao lúc này."));
        }
    }

    private static void accept(HttpExchange exchange, long handoverId) throws IOException {
        int receiverId;
        try { receiverId = authenticatedUserId(exchange); }
        catch (IllegalArgumentException error) { ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required")); return; }
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int outgoingId; String status;
                try (PreparedStatement query = connection.prepareStatement("SELECT outgoing_user_id,status FROM housekeeping_shift_handovers WHERE id=? FOR UPDATE")) {
                    query.setLong(1, handoverId);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next()) { connection.rollback(); ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy phiếu bàn giao.")); return; }
                        outgoingId = row.getInt(1); status = row.getString(2);
                    }
                }
                if (!"pending".equals(status)) throw new HandoverConflict("Phiếu bàn giao đã được xử lý; hãy làm mới danh sách.");
                if (outgoingId == receiverId) throw new HandoverConflict("Bạn không thể tự tiếp nhận phiếu bàn giao do mình tạo.");
                List<Long> taskIds = new ArrayList<>();
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT i.task_id,t.status,t.assigned_to_user_id FROM housekeeping_shift_handover_tasks i "
                                + "JOIN cleaning_tasks t ON t.id=i.task_id WHERE i.handover_id=? ORDER BY i.task_id FOR UPDATE")) {
                    query.setLong(1, handoverId);
                    try (ResultSet rows = query.executeQuery()) {
                        while (rows.next()) {
                            long taskId = rows.getLong(1); String taskStatus = rows.getString(2); int assignedId = rows.getInt(3); boolean unassigned = rows.wasNull();
                            if (unassigned || assignedId != outgoingId || !Set.of("pending", "in_progress").contains(taskStatus))
                                throw new HandoverConflict("Một công việc đã thay đổi trạng thái; phiếu chưa được chuyển. Tải lại danh sách.");
                            taskIds.add(taskId);
                        }
                    }
                }
                if (taskIds.isEmpty()) throw new HandoverConflict("Phiếu bàn giao không còn công việc hoạt động.");
                for (long taskId : taskIds) {
                    try (PreparedStatement update = connection.prepareStatement("UPDATE cleaning_tasks SET assigned_to_user_id=? WHERE id=? AND assigned_to_user_id=? AND status IN ('pending','in_progress')")) {
                        update.setInt(1, receiverId); update.setLong(2, taskId); update.setInt(3, outgoingId);
                        if (update.executeUpdate() != 1) throw new HandoverConflict("Một công việc vừa thay đổi; hãy tải lại danh sách.");
                    }
                    insertTaskEvent(connection, taskId, receiverId, "shift_handover_accepted", "Tiếp nhận bàn giao ca #" + handoverId);
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE housekeeping_shift_handovers SET status='accepted',accepted_by_user_id=?,accepted_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='pending'")) {
                    update.setInt(1, receiverId); update.setLong(2, handoverId);
                    if (update.executeUpdate() != 1) throw new HandoverConflict("Phiếu bàn giao vừa được xử lý; hãy tải lại danh sách.");
                }
                connection.commit();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(Map.of("handover_id", handoverId, "accepted_by_user_id", receiverId, "task_count", taskIds.size(), "status", "accepted")));
            } catch (HandoverConflict error) {
                connection.rollback(); ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(error.getMessage()));
            } catch (SQLException error) {
                connection.rollback(); throw error;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể tiếp nhận bàn giao lúc này."));
        }
    }

    private static void cancel(HttpExchange exchange, long handoverId) throws IOException {
        int userId;
        try { userId = authenticatedUserId(exchange); }
        catch (IllegalArgumentException error) { ApiResponseUtil.write(exchange, 401, ApiResponseUtil.error("A valid staff session is required")); return; }
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement query = connection.prepareStatement("SELECT outgoing_user_id,status FROM housekeeping_shift_handovers WHERE id=? FOR UPDATE")) {
                    query.setLong(1, handoverId);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next()) { connection.rollback(); ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy phiếu bàn giao.")); return; }
                        if (row.getInt(1) != userId) throw new HandoverConflict("Chỉ người tạo phiếu mới có thể hủy bàn giao.");
                        if (!"pending".equals(row.getString(2))) throw new HandoverConflict("Chỉ có thể hủy phiếu đang chờ tiếp nhận.");
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE housekeeping_shift_handovers SET status='cancelled',cancelled_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='pending'")) {
                    update.setLong(1, handoverId);
                    if (update.executeUpdate() != 1) throw new HandoverConflict("Phiếu bàn giao vừa được xử lý; hãy tải lại danh sách.");
                }
                try (PreparedStatement query = connection.prepareStatement("SELECT task_id FROM housekeeping_shift_handover_tasks WHERE handover_id=? ORDER BY task_id")) {
                    query.setLong(1, handoverId);
                    try (ResultSet rows = query.executeQuery()) { while (rows.next()) insertTaskEvent(connection, rows.getLong(1), userId, "shift_handover_cancelled", "Hủy phiếu bàn giao ca #" + handoverId + "; công việc vẫn do người tạo phụ trách"); }
                }
                connection.commit();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(Map.of("handover_id", handoverId, "status", "cancelled")));
            } catch (HandoverConflict error) {
                connection.rollback(); ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(error.getMessage()));
            } catch (SQLException error) {
                connection.rollback(); throw error;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException error) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Không thể hủy bàn giao lúc này."));
        }
    }

    private static List<Long> taskIds(JsonObject body) {
        if (body == null || !body.has("task_ids") || !body.get("task_ids").isJsonArray()) throw new IllegalArgumentException("task_ids is required");
        JsonArray values = body.getAsJsonArray("task_ids");
        if (values.size() == 0 || values.size() > 40) throw new IllegalArgumentException("Invalid task count");
        TreeSet<Long> unique = new TreeSet<>();
        for (var value : values) {
            long id = value.getAsLong();
            if (id <= 0 || !unique.add(id)) throw new IllegalArgumentException("Invalid or duplicate task ID");
        }
        return new ArrayList<>(unique);
    }

    private static HandoverTask loadTaskForOutgoing(Connection connection, long taskId, int userId) throws SQLException {
        String sql = "SELECT t.status,t.assigned_to_user_id,r.room_number,rt.name FROM cleaning_tasks t "
                + "JOIN rooms r ON r.id=t.room_id LEFT JOIN room_types rt ON rt.id=r.room_type_id WHERE t.id=? FOR UPDATE";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, taskId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                String status = row.getString(1); int assignedId = row.getInt(2);
                if (row.wasNull() || assignedId != userId || !Set.of("pending", "in_progress").contains(status)) return null;
                return new HandoverTask(taskId, row.getString(3), row.getString(4), status);
            }
        }
    }

    private static boolean hasPendingHandover(Connection connection, long taskId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM housekeeping_shift_handover_tasks i JOIN housekeeping_shift_handovers h ON h.id=i.handover_id WHERE i.task_id=? AND h.status='pending' LIMIT 1 FOR UPDATE")) {
            query.setLong(1, taskId);
            try (ResultSet row = query.executeQuery()) { return row.next(); }
        }
    }

    private static void insertTaskEvent(Connection connection, long taskId, int actorId, String type, String details) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO housekeeping_task_events(task_id,actor_user_id,event_type,details) VALUES(?,?,?,?)")) {
            insert.setLong(1, taskId); insert.setInt(2, actorId); insert.setString(3, type); insert.setString(4, details); insert.executeUpdate();
        }
    }

    private static int authenticatedUserId(HttpExchange exchange) {
        Object value = exchange.getAttribute("userId");
        if (!(value instanceof Number number) || number.intValue() <= 0) throw new IllegalArgumentException("Missing user identity");
        return number.intValue();
    }

    private static void addDate(JsonObject target, ResultSet rows, String column) throws SQLException {
        var value = rows.getObject(column, java.time.LocalDateTime.class);
        if (value == null) target.add(column, JsonNull.INSTANCE); else target.addProperty(column, value.toString());
    }

    private record HandoverTask(long id, String roomNumber, String roomType, String status) { }
    private static final class HandoverConflict extends RuntimeException { HandoverConflict(String message) { super(message); } }
}
