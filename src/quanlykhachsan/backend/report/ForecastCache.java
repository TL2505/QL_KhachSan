package quanlykhachsan.backend.report;

import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Forecasts only; authoritative room availability and booking writes never read this cache. */
public final class ForecastCache<T> {
    private record Entry<T>(T value,Instant expires){}
    private final ConcurrentHashMap<String,Entry<T>> values=new ConcurrentHashMap<>();
    private final Clock clock;
    public ForecastCache(Clock clock){this.clock=clock;}
    public T get(String key,Supplier<T> loader){
        Instant now=clock.instant();var existing=values.get(key);
        if(existing!=null&&existing.expires().isAfter(now))return existing.value();
        synchronized(this){
            existing=values.get(key);if(existing!=null&&existing.expires().isAfter(clock.instant()))return existing.value();
            values.entrySet().removeIf(entry->!entry.getValue().expires().isAfter(clock.instant()));
            if(values.size()>=90)values.clear();
            T result=loader.get();values.put(key,new Entry<>(result,clock.instant().plus(Duration.ofHours(6))));return result;
        }
    }
    public void clear(){values.clear();}
}
