package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Deterministic hotel charge calculation using decimal arithmetic only. */
public final class FinancialCalculator {
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private FinancialCalculator() { }

    /**
     * Calculates a room/service subtotal, service charge, VAT, discount and surcharge.
     * VAT is applied to the subtotal plus service charge and surcharge. Discount is
     * applied to the resulting gross amount, matching the documented total equation.
     */
    public static Calculation calculate(BigDecimal roomFee, BigDecimal discount,
                                         BigDecimal surcharge, TaxConfig taxConfig) {
        Objects.requireNonNull(taxConfig, "taxConfig");
        BigDecimal room = money(roomFee, "roomFee");
        BigDecimal discountAmount = money(discount, "discount");
        BigDecimal surchargeAmount = money(surcharge, "surcharge");
        BigDecimal serviceCharge = room.add(surchargeAmount)
            .multiply(taxConfig.getServiceChargeRate())
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        // Surcharges are part of the service-fee line, so the documented
        // Room Fee + Service Fee + VAT - Discount equation remains exact.
        BigDecimal serviceFee = serviceCharge.add(surchargeAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = room.add(serviceFee);
        BigDecimal vat = taxableAmount.multiply(taxConfig.getVatRate())
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal gross = taxableAmount.add(vat);
        if (discountAmount.compareTo(gross) > 0) {
            throw new IllegalArgumentException("discount cannot exceed gross amount");
        }
        BigDecimal total = gross.subtract(discountAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Calculation(room, serviceFee, vat, discountAmount, surchargeAmount, total,
            taxConfig.getVatRate(), taxConfig.getServiceChargeRate());
    }

    public static Calculation calculate(BigDecimal roomFee, BigDecimal discount, TaxConfig taxConfig) {
        return calculate(roomFee, discount, ZERO, taxConfig);
    }

    private static BigDecimal money(BigDecimal amount, String label) {
        Objects.requireNonNull(amount, label);
        if (amount.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative");
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static final class Calculation {
        private final BigDecimal roomFee;
        private final BigDecimal serviceFee;
        private final BigDecimal vat;
        private final BigDecimal discount;
        private final BigDecimal surcharge;
        private final BigDecimal totalAmount;
        private final BigDecimal vatRate;
        private final BigDecimal serviceChargeRate;

        private Calculation(BigDecimal roomFee, BigDecimal serviceFee, BigDecimal vat,
                            BigDecimal discount, BigDecimal surcharge, BigDecimal totalAmount,
                            BigDecimal vatRate, BigDecimal serviceChargeRate) {
            this.roomFee = roomFee;
            this.serviceFee = serviceFee;
            this.vat = vat;
            this.discount = discount;
            this.surcharge = surcharge;
            this.totalAmount = totalAmount;
            this.vatRate = vatRate;
            this.serviceChargeRate = serviceChargeRate;
        }

        public BigDecimal getRoomFee() { return roomFee; }
        public BigDecimal getServiceFee() { return serviceFee; }
        public BigDecimal getVat() { return vat; }
        public BigDecimal getDiscount() { return discount; }
        public BigDecimal getSurcharge() { return surcharge; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public BigDecimal getVatRate() { return vatRate; }
        public BigDecimal getServiceChargeRate() { return serviceChargeRate; }
    }
}
