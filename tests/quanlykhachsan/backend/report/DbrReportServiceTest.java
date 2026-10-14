package quanlykhachsan.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DbrReportServiceTest {
    @Test
    void computesRevenueOccupancyAdrAndRevparExactly() {
        DbrLedgerDao.DbrMetrics d = DbrReportService.calculate(new DbrLedgerDao.DbrInputs(
            new BigDecimal("1250.10"), new BigDecimal("900.00"), 30, 200));
        assertEquals(new BigDecimal("1250.10"), d.totalRevenue());
        assertEquals(new BigDecimal("900.00"), d.roomRevenue());
        assertEquals(new BigDecimal("15.00"), d.occupancyRate());
        assertEquals(new BigDecimal("30.00"), d.adr());
        assertEquals(new BigDecimal("4.50"), d.revpar());
    }

    @Test
    void zeroRoomAndZeroOccupancyNeverDivideByZero() {
        DbrLedgerDao.DbrMetrics d = DbrReportService.calculate(new DbrLedgerDao.DbrInputs(
            BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        assertEquals(new BigDecimal("0.00"), d.occupancyRate());
        assertEquals(new BigDecimal("0.00"), d.adr());
        assertEquals(new BigDecimal("0.00"), d.revpar());
    }
}
