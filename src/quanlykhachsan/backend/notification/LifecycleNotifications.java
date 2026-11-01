package quanlykhachsan.backend.notification;

import java.sql.*;

/** Both channels are queued in the business transaction; rollback also rolls back the event. */
public final class LifecycleNotifications {
    private LifecycleNotifications() { }
    public static void booking(Connection c,int booking,String type,String key,String title,String message)throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Notification events require a business transaction");
        int customer;String email,room;Timestamp checkIn,checkOut;java.math.BigDecimal total;
        try(var q=c.prepareStatement("SELECT b.customer_id,c.email,r.room_number,b.check_in_date,b.check_out_date,b.total_price FROM bookings b JOIN customers c ON c.id=b.customer_id JOIN rooms r ON r.id=b.room_id WHERE b.id=?")){
            q.setInt(1,booking);try(var r=q.executeQuery()){if(!r.next())throw new SQLException("Notification booking missing");customer=r.getInt(1);email=r.getString(2);room=r.getString(3);checkIn=r.getTimestamp(4);checkOut=r.getTimestamp(5);total=r.getBigDecimal(6);}
        }
        String body=message+"\nMã đặt phòng: "+booking+".\nBạn có thể xem chi tiết trong cổng khách hàng Aurelia.";
        if(java.util.Set.of("booking_created","booking_confirmed","booking_cancelled","checkout_gratitude").contains(type)){
            body+="\nPhòng: "+room+".\nNhận phòng: "+checkIn.toLocalDateTime().toString().replace('T',' ')+".\nTrả phòng: "+checkOut.toLocalDateTime().toString().replace('T',' ')+".\nTổng tiền đặt phòng: "+total.toPlainString()+" VND.";
        }
        try(var q=c.prepareStatement("INSERT INTO customer_notifications(customer_id,booking_id,notification_type,title,message,action_url,event_key) VALUES(?,?,?,?,?,'bookings',?) ON DUPLICATE KEY UPDATE event_key=event_key")){
            q.setInt(1,customer);q.setInt(2,booking);q.setString(3,type);q.setString(4,title);q.setString(5,body);q.setString(6,key);q.executeUpdate();
        }
        try(var q=c.prepareStatement("INSERT INTO notification_outbox(event_key,customer_id,booking_id,event_type,recipient,subject,body,state,last_error) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_key=event_key")){
            q.setString(1,key);q.setInt(2,customer);q.setInt(3,booking);q.setString(4,type);q.setString(5,email);q.setString(6,title);q.setString(7,body);
            boolean missing=email==null||email.isBlank();q.setString(8,missing?"SKIPPED":"PENDING");q.setString(9,missing?"MISSING_EMAIL":null);q.executeUpdate();
        }
    }
    public static void payment(Connection c,int booking,String key,java.math.BigDecimal amount,java.math.BigDecimal remaining)throws SQLException {
        booking(c,booking,remaining.signum()==0?"payment_paid":"payment_partial","payment:"+key,
            remaining.signum()==0?"Đã nhận đủ thanh toán":"Đã nhận thanh toán một phần",
            "Đã nhận "+amount.toPlainString()+" VND. Còn phải thanh toán "+remaining.toPlainString()+" VND.");
    }
}
