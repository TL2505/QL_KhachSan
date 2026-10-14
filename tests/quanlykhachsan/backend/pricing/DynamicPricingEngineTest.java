package quanlykhachsan.backend.pricing;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicPricingEngineTest {
    @ParameterizedTest @CsvSource({"0,200,1.00","1,200,1.00","98,200,1.00","99,200,1.00","100,200,1.15","101,200,1.15","139,200,1.15","140,200,1.30","141,200,1.30","169,200,1.30","170,200,1.50","171,200,1.50","200,200,1.50","85,100,1.50","69,100,1.15"})
    void tiersUseExactBoundaries(int occupied,int operational,String expected) {
        var multiplier=PricingTier.forOccupancy(occupied,operational).multiplier();assertEquals(new BigDecimal(expected),multiplier);
        assertEquals(new BigDecimal("1000000.00").multiply(multiplier).setScale(2),DynamicPricingEngine.nightly(new BigDecimal("1000000"),multiplier));
    }
    @Test void invalidInventoryAndRoundingAreExplicit() {
        assertThrows(IllegalArgumentException.class,()->PricingTier.forOccupancy(0,0));
        assertThrows(IllegalArgumentException.class,()->PricingTier.forOccupancy(201,200));
        assertEquals(new BigDecimal("1.16"),DynamicPricingEngine.nightly(new BigDecimal("1.005"),new BigDecimal("1.15")));
    }
}
