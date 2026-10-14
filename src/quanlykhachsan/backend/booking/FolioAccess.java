package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.HttpExchange;
import java.sql.*;
import java.math.BigDecimal;

/** Payment queries require a financial staff role or ownership established by guest JWT. */
final class FolioAccess {
    static State find(Connection c,HttpExchange e,String identifier)throws SQLException {
        if(!identifier.matches("[A-Za-z0-9-]{1,64}"))throw new IllegalArgumentException();
        String role=String.valueOf(e.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("admin","front_desk","guest").contains(role))throw new SecurityException();
        boolean numeric=identifier.matches("[0-9]+");
        try(PreparedStatement q=c.prepareStatement("SELECT f.id,f.code,f.balance,f.status,b.customer_id,(SELECT MAX(created_at) FROM folio_transactions t WHERE t.folio_id=f.id AND t.charge_type='payment') FROM folios f JOIN bookings b ON b.id=f.booking_id WHERE f."+(numeric?"id":"code")+"=?")) {
            if(numeric){long id=Long.parseLong(identifier);if(id<1)throw new IllegalArgumentException();q.setLong(1,id);}else q.setString(1,identifier);
            try(ResultSet r=q.executeQuery()) {
                if(!r.next())return null;
                if(role.equals("guest") && (!(e.getAttribute("customer_id") instanceof Number) || ((Number)e.getAttribute("customer_id")).intValue()!=r.getInt(5)))throw new SecurityException();
                Timestamp last=r.getTimestamp(6);return new State(r.getLong(1),r.getString(2),r.getBigDecimal(3),r.getString(4),last==null?null:last.toLocalDateTime().toString());
            }
        }
    }
    record State(long id,String code,BigDecimal balance,String status,String lastPayment) { }
}
