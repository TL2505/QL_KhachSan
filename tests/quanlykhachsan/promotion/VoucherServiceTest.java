package quanlykhachsan.promotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import quanlykhachsan.backend.promotion.VoucherService;
import quanlykhachsan.backend.booking.*;
class VoucherServiceTest {
    @Test void capAndTaxOrderAreExact() {
        var discount=VoucherService.calculateDiscount(new BigDecimal("3000000"),"percentage",new BigDecimal("20"),new BigDecimal("500000"));
        assertEquals(new BigDecimal("500000.00"),discount);
        var total=FinancialCalculator.calculate(new BigDecimal("3000000").subtract(discount),BigDecimal.ZERO,TaxConfig.standardEightPercentVat());
        assertEquals(new BigDecimal("125000.00"),total.getServiceFee());
        assertEquals(new BigDecimal("210000.00"),total.getVat());
        assertEquals(new BigDecimal("2835000.00"),total.getTotalAmount());
        assertEquals(new BigDecimal("100.00"),VoucherService.calculateDiscount(new BigDecimal("100"),"fixed_amount",new BigDecimal("500"),new BigDecimal("500")));
        assertThrows(IllegalArgumentException.class,()->VoucherService.calculateDiscount(BigDecimal.ONE,"percentage",new BigDecimal("101"),BigDecimal.TEN));
    }
}
