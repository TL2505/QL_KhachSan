package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Configurable token burst, replenished at one token/second. Remote socket IP is authoritative. */
public final class IpRateLimiter extends Filter {
    private record Bucket(double tokens, long updated) { }
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    private final int capacity;
    private final int burstTokens;
    private final java.util.Set<java.net.InetAddress> trustedProxies;
    public IpRateLimiter() { this(Clock.systemUTC(),10_000,proxyAddresses(),60); }
    public IpRateLimiter(int burstTokens) { this(Clock.systemUTC(),10_000,proxyAddresses(),burstTokens); }
    public IpRateLimiter(Clock clock, int capacity) {
        this(clock,capacity,java.util.Set.of(),60);
    }
    public IpRateLimiter(Clock clock,int capacity,java.util.Set<java.net.InetAddress> trustedProxies){
        this(clock,capacity,trustedProxies,60);
    }
    public IpRateLimiter(Clock clock,int capacity,java.util.Set<java.net.InetAddress> trustedProxies,int burstTokens){
        if (capacity < 1 || burstTokens < 1) throw new IllegalArgumentException("Positive rate limiter capacities required");
        this.clock = java.util.Objects.requireNonNull(clock); this.capacity = capacity;
        this.burstTokens = burstTokens;
        this.trustedProxies=java.util.Set.copyOf(trustedProxies);
    }
    private static java.util.Set<java.net.InetAddress> proxyAddresses(){
        var result=new java.util.HashSet<java.net.InetAddress>();String configured=System.getenv("TRUSTED_PROXY_IPS");
        if(configured!=null&&!configured.isBlank())for(String value:configured.split(",")){
            var address=literal(value.trim());if(address==null)throw new IllegalStateException("TRUSTED_PROXY_IPS requires literal IP addresses");result.add(address);
        }return result;
    }
    private static java.net.InetAddress literal(String value){
        if(value==null)return null;
        boolean v4=value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"),v6=value.contains(":")&&value.matches("[0-9a-fA-F:]+");
        if(!v4&&!v6)return null;
        if(v4)for(String part:value.split("\\."))if(Integer.parseInt(part)>255)return null;
        try{return java.net.InetAddress.getByName(value);}catch(java.net.UnknownHostException ex){return null;}
    }

    public synchronized boolean tryAcquire(String ip) {
        long now = clock.millis();
        Bucket previous = buckets.get(ip);
        if (previous == null) {
            // Only fully replenished buckets may be discarded. Never reset an active offender.
            buckets.entrySet().removeIf(e -> now - e.getValue().updated() >= 60_000);
            if (buckets.size() >= capacity) return false;
            previous = new Bucket(burstTokens, now);
        }
        double tokens = Math.min(burstTokens, previous.tokens() + Math.max(0, now - previous.updated()) / 1000.0);
        boolean allowed = tokens >= 1;
        buckets.put(ip, new Bucket(allowed ? tokens - 1 : tokens, Math.max(now, previous.updated())));
        return allowed;
    }

    @Override public String description() { return "Unauthenticated remote-IP token bucket"; }
    @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        boolean authenticated = false;
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                SecurityUtil.validateAndExtractClaims(auth.substring(7).trim());
                authenticated = true;
            } catch (JwtException | IllegalArgumentException ignored) { /* Invalid credentials consume the IP budget. */ }
        }
        var source=exchange.getRemoteAddress().getAddress();
        if(trustedProxies.contains(source)){
            var forwarded=literal(exchange.getRequestHeaders().getFirst("X-Real-IP"));if(forwarded!=null)source=forwarded;
        }
        if (!authenticated && !tryAcquire(source.getHostAddress())) {
            exchange.getResponseHeaders().set("Retry-After", "1");
            ApiResponseUtil.sendError(exchange, 429, "ERR_429_RATE_LIMITED", "Request limit exceeded"); return;
        }
        chain.doFilter(exchange);
    }
}
