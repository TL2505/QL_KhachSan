package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.HttpExchange;
import java.sql.*;
import java.math.BigDecimal;

/** Payment queries require staff access or ownership by an authenticated customer/guest. */
final class FolioAccess {
    static State find(Connection c,HttpExchange e,String identifier)throws SQLException {
        if(!identifier.matches("[A-Za-z0-9-]{1,64}"))throw new IllegalArgumentException();
        String role=String.valueOf(e.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("admin","front_desk","guest","customer").contains(role))throw new SecurityException();
        boolean numeric=identifier.matches("[0-9]+");
        try(PreparedStatement q=c.prepareStatement("SELECT f.id,f.code,f.balance,f.status,b.customer_id,(SELECT MAX(created_at) FROM folio_transactions t WHERE t.folio_id=f.id AND t.charge_type='payment') FROM folios f JOIN bookings b ON b.id=f.booking_id WHERE f."+(numeric?"id":"code")+"=?")) {
            if(numeric){long id=Long.parseLong(identifier);if(id<1)throw new IllegalArgumentException();q.setLong(1,id);}else q.setString(1,identifier);
            long id;String code,status;BigDecimal balance;int customerId;Timestamp last;
            try(ResultSet r=q.executeQuery()) {
                if(!r.next())return null;
                id=r.getLong(1);code=r.getString(2);balance=r.getBigDecimal(3);status=r.getString(4);customerId=r.getInt(5);last=r.getTimestamp(6);
            }
            if(role.equals("guest") && (!(e.getAttribute("customer_id") instanceof Number) || ((Number)e.getAttribute("customer_id")).intValue()!=customerId))throw new SecurityException();
            if(role.equals("customer")) {
                Object user=e.getAttribute("userId");
                if(!(user instanceof Number))throw new SecurityException();
                try(PreparedStatement owner=c.prepareStatement("SELECT customer_id FROM users WHERE id=?")) {
                    owner.setInt(1,((Number)user).intValue());
                    try(ResultSet account=owner.executeQuery()) {
                        if(!account.next() || account.getObject(1)==null || account.getInt(1)!=customerId)throw new SecurityException();
                    }
                }
            }
            return new State(id,code,balance,status,last==null?null:last.toLocalDateTime().toString());
        }
    }
    record State(long id,String code,BigDecimal balance,String status,String lastPayment) { }
}
