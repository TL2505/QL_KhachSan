package quanlykhachsan.backend.pricing;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.booking.TaxConfig;
/** Bounded 250ms preview cache. Financial writes always call the engine directly. */
public final class PricingQuoteCache {
    private static final Map<Key,Entry> CACHE=new LinkedHashMap<>(128,0.75f,true);
    private record Key(int category,PortalValidation.DateRange dates,String vat) { }
    private record Entry(DynamicPricingEngine.Quote quote,long expires) { }
    public static DynamicPricingEngine.Quote preview(int category,PortalValidation.DateRange dates)throws SQLException {
        Key key=new Key(category,dates,TaxConfig.fromRuntimeConfiguration().getVatRate().toPlainString());
        synchronized(CACHE) {
            Entry entry=CACHE.get(key);if(entry!=null && System.nanoTime()<entry.expires())return entry.quote();
            try(Connection c=DatabaseConnection.getConnection()) {
                var quote=new DynamicPricingEngine().quote(c,category,dates);
                if(CACHE.size()>=256)CACHE.remove(CACHE.keySet().iterator().next());
                CACHE.put(key,new Entry(quote,System.nanoTime()+250_000_000L));return quote;
            }
        }
    }
    public static void invalidate(){synchronized(CACHE){CACHE.clear();}}
}
