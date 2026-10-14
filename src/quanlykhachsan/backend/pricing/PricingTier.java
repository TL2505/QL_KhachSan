package quanlykhachsan.backend.pricing;

import java.math.BigDecimal;

public enum PricingTier {
    BASE("1.00"), STANDARD("1.15"), HIGH("1.30"), PEAK("1.50");
    private final BigDecimal multiplier;
    PricingTier(String value) { multiplier=new BigDecimal(value); }
    public BigDecimal multiplier() { return multiplier; }
    public static PricingTier forOccupancy(int occupied,int operational) {
        if(operational<=0 || occupied<0 || occupied>operational) throw new IllegalArgumentException("Invalid occupancy");
        long percent=occupied*100L;
        if(percent>=85L*operational)return PEAK;
        if(percent>=70L*operational)return HIGH;
        if(percent>=50L*operational)return STANDARD;
        return BASE;
    }
}
