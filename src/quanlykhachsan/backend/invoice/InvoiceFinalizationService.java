package quanlykhachsan.backend.invoice;
import java.sql.*;
import java.math.BigDecimal;
/** Finalize the paid accounting record in the checkout transaction, without repricing. */
public final class InvoiceFinalizationService {
    public static void finalizePaid(Connection c,int booking)throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Invoice finalization requires the checkout transaction");
        try(PreparedStatement q=c.prepareStatement("SELECT b.status,COALESCE(SUM(f.balance),0) FROM bookings b JOIN folios f ON f.booking_id=b.id WHERE b.id=? GROUP BY b.id,b.status FOR UPDATE")) {
            q.setInt(1,booking);try(ResultSet r=q.executeQuery()){if(!r.next() || !"checked_out".equals(r.getString(1)) || r.getBigDecimal(2).signum()!=0)throw new SQLException("Only settled checked-out bookings can finalize an invoice");}
        }
        BigDecimal room=BigDecimal.ZERO,service=BigDecimal.ZERO,vat=BigDecimal.ZERO;
        try(PreparedStatement q=c.prepareStatement("SELECT t.charge_type,SUM(CASE WHEN t.transaction_type='debit' THEN t.amount ELSE -t.amount END) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=? AND t.charge_type NOT IN ('payment','credit_adjustment') GROUP BY t.charge_type")) {
            q.setInt(1,booking);try(ResultSet r=q.executeQuery()){while(r.next()){String type=r.getString(1);if(type.equals("room_rate"))room=r.getBigDecimal(2);else if(type.equals("vat"))vat=r.getBigDecimal(2);else service=service.add(r.getBigDecimal(2));}}
        }
        BigDecimal total=room.add(service).add(vat);
        try(PreparedStatement q=c.prepareStatement("SELECT final_total,status FROM invoices WHERE booking_id=? FOR UPDATE")) {
            q.setInt(1,booking);try(ResultSet r=q.executeQuery()){if(r.next()){if(r.getBigDecimal(1).compareTo(total)!=0 || "cancelled".equals(r.getString(2)))throw new SQLException("Existing invoice requires reconciliation");try(PreparedStatement update=c.prepareStatement("UPDATE invoices SET status='paid' WHERE booking_id=?")){update.setInt(1,booking);update.executeUpdate();}return;}}
        }
        try(PreparedStatement q=c.prepareStatement("INSERT INTO invoices(booking_id,total_room_fee,total_service_fee,discount,tax_amount,final_total,status) VALUES (?,?,?,0,?,?,'paid')")){q.setInt(1,booking);q.setBigDecimal(2,room);q.setBigDecimal(3,service);q.setBigDecimal(4,vat);q.setBigDecimal(5,total);q.executeUpdate();}
    }
}
