package quanlykhachsan.backend.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FinancialCalculatorTest {
    static Stream<Arguments> pricingCases() {
        return Stream.of(
            row("100.00", "0.00", "0.00", "0.08"), row("100.00", "0.00", "0.00", "0.10"),
            row("0.10", "0.00", "0.00", "0.08"), row("0.10", "0.00", "0.00", "0.10"),
            row("100.00", "10.00", "20.00", "0.08"), row("100.00", "10.00", "20.00", "0.10"),
            row("250.25", "0.00", "0.00", "0.08"), row("250.25", "5.25", "0.00", "0.10"),
            row("999.99", "99.99", "12.34", "0.08"), row("999.99", "99.99", "12.34", "0.10"),
            row("1.00", "0.00", "0.01", "0.08"), row("1.00", "0.00", "0.01", "0.10"),
            row("1234.56", "100.00", "88.88", "0.08"), row("1234.56", "100.00", "88.88", "0.10"),
            row("50.55", "1.11", "2.22", "0.08"), row("50.55", "1.11", "2.22", "0.10"),
            row("10000.00", "0.00", "0.00", "0.08"), row("10000.00", "500.00", "0.00", "0.10"),
            row("0.00", "0.00", "0.00", "0.08"), row("17.45", "0.00", "3.21", "0.10")
        );
    }

    private static Arguments row(String room, String discount, String surcharge, String vat) {
        return Arguments.of(new BigDecimal(room), new BigDecimal(discount), new BigDecimal(surcharge),
            new TaxConfig(new BigDecimal(vat)));
    }

    @ParameterizedTest
    @MethodSource("pricingCases")
    void totalsBalanceToExactlyZeroCents(BigDecimal room, BigDecimal discount,
                                        BigDecimal surcharge, TaxConfig config) {
        FinancialCalculator.Calculation result = FinancialCalculator.calculate(room, discount, surcharge, config);
        BigDecimal equation = result.getRoomFee().add(result.getServiceFee()).add(result.getVat())
            .subtract(result.getDiscount());
        assertEquals(0, equation.compareTo(result.getTotalAmount()), "pricing equation must reconcile exactly");
    }

    @Test
    void roundsHalfUpAndRejectsInvalidTaxRate() {
        FinancialCalculator.Calculation result = FinancialCalculator.calculate(new BigDecimal("0.10"),
            BigDecimal.ZERO, TaxConfig.standardEightPercentVat());
        assertEquals(new BigDecimal("0.01"), result.getServiceFee());
        assertEquals(new BigDecimal("0.01"), result.getVat());
        assertThrows(IllegalArgumentException.class, () -> new TaxConfig(new BigDecimal("0.09")));
    }
}
