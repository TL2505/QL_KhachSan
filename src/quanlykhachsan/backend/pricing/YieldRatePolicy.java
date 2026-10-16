package quanlykhachsan.backend.pricing;

import java.math.BigDecimal;

/** Technical floor differs from the default approval floor; all arithmetic is decimal. */
public final class YieldRatePolicy {
    private static final BigDecimal HARD_FLOOR=new BigDecimal("0.70"),DEFAULT_FLOOR=new BigDecimal("0.80"),CEILING=new BigDecimal("2.50");
    private YieldRatePolicy(){}
    public static BigDecimal recommendation(BigDecimal value){
        if(value==null)throw new IllegalArgumentException("Multiplier required");
        return value.max(DEFAULT_FLOOR).min(CEILING).setScale(2,java.math.RoundingMode.HALF_UP);
    }
    public static BigDecimal approve(BigDecimal value,boolean administrator,String overrideReason){
        if(value==null||value.compareTo(HARD_FLOOR)<0||value.compareTo(CEILING)>0||value.stripTrailingZeros().scale()>2)
            throw new IllegalArgumentException("Multiplier must be within [0.70,2.50] with at most two decimal places");
        if(value.compareTo(DEFAULT_FLOOR)<0&&(!administrator||overrideReason==null||overrideReason.isBlank()))
            throw new SecurityException("Below 0.80 requires an explicit Admin/GM override reason");
        if(overrideReason!=null&&(overrideReason.length()>255||overrideReason.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("Invalid override reason");
        return value.setScale(2);
    }
}
