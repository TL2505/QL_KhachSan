package quanlykhachsan.backend.ai;

import java.time.Clock;
import java.util.*;

/** Fixed rolling minute: at most ten calls per verified token; client session IDs cannot reset it. */
public final class SessionRateLimiter {
    private final Map<String,ArrayDeque<Long>> calls=new HashMap<>();
    private final Clock clock;
    public SessionRateLimiter(Clock clock){this.clock=clock;}
    public synchronized boolean acquire(String tokenFingerprint){
        long now=clock.millis();
        if(!calls.containsKey(tokenFingerprint)){
            calls.entrySet().removeIf(entry->entry.getValue().isEmpty()||entry.getValue().peekLast()<=now-60_000);
            if(calls.size()>=10_000)return false;
        }
        var window=calls.computeIfAbsent(tokenFingerprint,key->new ArrayDeque<>());
        while(!window.isEmpty()&&window.peekFirst()<=now-60_000)window.removeFirst();
        if(window.size()>=10)return false;window.addLast(now);return true;
    }
}
