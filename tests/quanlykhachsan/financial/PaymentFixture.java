package quanlykhachsan.financial;

import java.sql.*;
import java.math.BigDecimal;
import java.util.UUID;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Uses seeded parent rows only in the disposable schema; cleans every created child. */
public final class PaymentFixture implements AutoCloseable {
    public final long folio;public final int booking;public final String code;
    public PaymentFixture(BigDecimal balance)throws SQLException {
        code="FOLIO-T-"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        try(Connection c=DatabaseConnection.getConnection()) {
            try(PreparedStatement q=c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,total_price,status,customer_type) SELECT (SELECT MIN(id) FROM customers),(SELECT MIN(id) FROM rooms),CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),?,'confirmed','Web'",Statement.RETURN_GENERATED_KEYS)) {
                q.setBigDecimal(1,balance);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();booking=r.getInt(1);}
            }
            try(PreparedStatement q=c.prepareStatement("INSERT INTO folios(booking_id,balance,code) VALUES (?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                q.setInt(1,booking);q.setBigDecimal(2,balance);q.setString(3,code);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();folio=r.getLong(1);}
            }
            try(PreparedStatement q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) VALUES (?,'debit','room_rate',?,CURRENT_DATE)")){q.setLong(1,folio);q.setBigDecimal(2,balance);q.executeUpdate();}
        }
    }
    @Override public void close()throws SQLException {
        try(Connection c=DatabaseConnection.getConnection()) {
            for(String sql:new String[]{"DELETE d FROM bank_webhook_deliveries d JOIN bank_webhook_logs l ON l.bank_trans_id=d.bank_trans_id WHERE l.folio_id=?","DELETE FROM bank_webhook_logs WHERE folio_id=?","DELETE FROM folios WHERE id=?"})try(PreparedStatement q=c.prepareStatement(sql)){q.setLong(1,folio);q.executeUpdate();}
            try(PreparedStatement q=c.prepareStatement("DELETE FROM bookings WHERE id=?")){q.setInt(1,booking);q.executeUpdate();}
        }
    }
}
