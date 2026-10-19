package quanlykhachsan.backend.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RevenueHistoryPolicyTest {
    @Test void validHistoryProducesExactAdrRevparAndAdvisory(){
        LocalDate today=LocalDate.of(2026,10,20);var days=new ArrayList<RevenueHistoryPolicy.Day>();
        for(int i=1;i<=7;i++)days.add(new RevenueHistoryPolicy.Day(today.minusDays(i),new BigDecimal("900.00"),3,10));
        var result=RevenueHistoryPolicy.recommend(days,today,today,8,10);
        assertEquals(new BigDecimal("300.00"),result.adr());assertEquals(new BigDecimal("90.00"),result.revpar());
        assertEquals(new BigDecimal("80.00"),result.predicted_occupancy());assertEquals(new BigDecimal("1.25"),result.recommended_multiplier());
    }
    @Test void insufficientHistoryNeverInventsRevenueOrRecommendations(){
        var today=LocalDate.of(2026,10,20);var r=RevenueHistoryPolicy.recommend(List.of(),today,today,0,10);
        assertEquals("INSUFFICIENT_HISTORY",r.source());assertNull(r.adr());assertNull(r.revpar());assertNull(r.recommended_multiplier());
        assertThrows(IllegalArgumentException.class,()->RevenueHistoryPolicy.recommend(List.of(new RevenueHistoryPolicy.Day(today.minusDays(1),BigDecimal.TEN,0,10)),today,today,0,10));
        assertThrows(IllegalArgumentException.class,()->RevenueHistoryPolicy.recommend(List.of(),today,today,11,10));
    }
}
