package quanlykhachsan.backend.customer;

import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Read-only staff directory. Stored tiers and point balances are never recalculated. */
public final class LoyaltyDirectoryService {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    public LoyaltyDirectoryService() { this(DatabaseConnection::getConnection); }
    public LoyaltyDirectoryService(Connections connections) { this.connections = Objects.requireNonNull(connections); }

    public Map<String, Object> directory() throws SQLException {
        try (Connection connection = connections.open()) {
            List<Map<String, Object>> members = new ArrayList<>();
            Map<String, Integer> summary = new LinkedHashMap<>();
            for (String tier : List.of("total", "Silver", "Gold", "VIP", "other")) summary.put(tier, 0);
            // Deliberately independent of the customer-search endpoint's 50-row limit.
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, full_name, phone, loyalty_level, loyalty_points, total_loyalty_points, created_at FROM customers ORDER BY created_at DESC, id DESC");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    Map<String, Object> member = member(rows);
                    members.add(member);
                    summary.merge("total", 1, Integer::sum);
                    String tier = String.valueOf(member.get("loyaltyLevel")).trim().toLowerCase(Locale.ROOT);
                    String key = switch (tier) { case "silver" -> "Silver"; case "gold" -> "Gold"; case "vip" -> "VIP"; default -> "other"; };
                    summary.merge(key, 1, Integer::sum);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("members", members); result.put("summary", summary);
            return result;
        }
    }

    /** null means an unknown customer; an empty history means a known customer with no ledger entries. */
    public Map<String, Object> history(int customerId) throws SQLException {
        if (customerId < 1) throw new IllegalArgumentException("Positive customer ID required");
        try (Connection connection = connections.open()) {
            Map<String, Object> member;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, full_name, phone, loyalty_level, loyalty_points, total_loyalty_points, created_at FROM customers WHERE id = ?")) {
                query.setInt(1, customerId);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) return null;
                    member = member(rows);
                }
            }
            List<Map<String, Object>> history = new ArrayList<>();
            // This is the ledger used by the schema, migrations and review-point writes.
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, points, type, description, created_at FROM loyalty_histories WHERE customer_id = ? ORDER BY created_at DESC, id DESC")) {
                query.setInt(1, customerId);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id", rows.getInt("id")); entry.put("pointsChange", rows.getObject("points"));
                        entry.put("type", rows.getString("type")); entry.put("description", rows.getString("description"));
                        entry.put("createdAt", timestamp(rows.getTimestamp("created_at")));
                        history.add(entry);
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("member", member); result.put("history", history);
            return result;
        }
    }

    private static Map<String, Object> member(ResultSet rows) throws SQLException {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("id", rows.getInt("id")); member.put("fullName", rows.getString("full_name"));
        member.put("phone", rows.getString("phone")); member.put("loyaltyLevel", rows.getString("loyalty_level"));
        member.put("loyaltyPoints", rows.getObject("loyalty_points"));
        member.put("totalLoyaltyPoints", rows.getObject("total_loyalty_points"));
        member.put("joinedAt", timestamp(rows.getTimestamp("created_at")));
        return member;
    }
    private static String timestamp(Timestamp value) { return value == null ? null : value.toLocalDateTime().toString(); }
}
