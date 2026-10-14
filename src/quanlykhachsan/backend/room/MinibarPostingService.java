package quanlykhachsan.backend.room;

import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import com.google.gson.JsonObject;

public final class MinibarPostingService {
    /** Caller owns the transaction and room lock; no inventory or ledger write can escape rollback. */
    public JsonObject post(Connection c,int room,List<Item> items,String reference)throws SQLException {
        if(items==null || items.isEmpty() || items.size()>50)throw new IllegalArgumentException("Invalid minibar items");
        Set<Integer> ids=new HashSet<>();for(Item item:items)if(item.id()<1 || item.quantity()<1 || item.quantity()>1000 || !ids.add(item.id()))throw new IllegalArgumentException("Invalid minibar quantity or duplicate item");
        try(PreparedStatement q=c.prepareStatement("SELECT status FROM rooms WHERE id=? FOR UPDATE")){q.setInt(1,room);try(ResultSet r=q.executeQuery()){if(!r.next() || !Set.of("occupied_clean","occupied_dirty").contains(r.getString(1)))throw new IllegalStateException("Minibar requires an occupied room");}}
        int booking;
        try(PreparedStatement q=c.prepareStatement("SELECT id FROM bookings WHERE room_id=? AND status='checked_in' ORDER BY id LIMIT 2 FOR UPDATE")){q.setInt(1,room);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalStateException("Active booking not found");booking=r.getInt(1);if(r.next())throw new IllegalStateException("Ambiguous active booking requires Front Desk review");}}
        BigDecimal total=new BigDecimal("0.00");List<Item> ordered=new ArrayList<>(items);ordered.sort(Comparator.comparingInt(Item::id));
        for(Item item:ordered) {
            BigDecimal price;int stock;
            try(PreparedStatement q=c.prepareStatement("SELECT unit_price,current_stock FROM minibar_items WHERE id=? AND room_id=? FOR UPDATE")){q.setInt(1,item.id());q.setInt(2,room);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown minibar item");price=r.getBigDecimal(1);stock=r.getInt(2);}}
            if(item.quantity()>stock)throw new IllegalStateException("Insufficient minibar stock");
            try(PreparedStatement q=c.prepareStatement("UPDATE minibar_items SET current_stock=current_stock-? WHERE id=? AND room_id=? AND current_stock>=?")){q.setInt(1,item.quantity());q.setInt(2,item.id());q.setInt(3,room);q.setInt(4,item.quantity());if(q.executeUpdate()!=1)throw new IllegalStateException("Minibar stock changed");}
            total=total.add(price.multiply(BigDecimal.valueOf(item.quantity())));
        }
        long folio;
        // The locked booking row serializes guest-folio creation without index gap locks.
        try(PreparedStatement q=c.prepareStatement("SELECT id FROM folios WHERE booking_id=? AND folio_type='guest' ORDER BY id LIMIT 1")){q.setInt(1,booking);try(ResultSet r=q.executeQuery()){folio=r.next()?r.getLong(1):0;}}
        if(folio==0)try(PreparedStatement q=c.prepareStatement("INSERT INTO folios(booking_id,folio_type,balance,status,code) VALUES(?,'guest',0,'open',?)",Statement.RETURN_GENERATED_KEYS)){q.setInt(1,booking);q.setString(2,"FOLIO-GUEST-B"+booking);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Guest folio ID missing");folio=r.getLong(1);}}
        long transaction;
        try(PreparedStatement q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'debit','minibar','Audited minibar consumption',?,business_date,? FROM hotel_business_state WHERE id=1",Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1,folio);q.setBigDecimal(2,total);q.setString(3,reference);if(q.executeUpdate()!=1)throw new SQLException("Hotel business date missing");try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Minibar transaction ID missing");transaction=r.getLong(1);}
        }
        try(PreparedStatement q=c.prepareStatement("UPDATE folios SET balance=balance+?,status='open' WHERE id=? AND status<>'closed'")){q.setBigDecimal(1,total);q.setLong(2,folio);if(q.executeUpdate()!=1)throw new IllegalStateException("Guest folio is closed");}
        JsonObject data=new JsonObject();data.addProperty("transaction_id",transaction);data.addProperty("folio_id",folio);data.addProperty("amount",total);return data;
    }
    public record Item(int id,int quantity) { }
}
