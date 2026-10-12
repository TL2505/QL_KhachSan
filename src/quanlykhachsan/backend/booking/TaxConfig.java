package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable, explicit tax and service-charge rates for a financial calculation. */
public final class TaxConfig {
    public static final BigDecimal VAT_8_PERCENT = new BigDecimal("0.08");
    public static final BigDecimal VAT_10_PERCENT = new BigDecimal("0.10");
    public static final BigDecimal LUXURY_SERVICE_CHARGE = new BigDecimal("0.05");

    private final BigDecimal vatRate;

    public TaxConfig(BigDecimal vatRate) {
        this.vatRate = validateRate(vatRate, "VAT rate");
        if (this.vatRate.compareTo(VAT_8_PERCENT) != 0 && this.vatRate.compareTo(VAT_10_PERCENT) != 0) {
            throw new IllegalArgumentException("VAT rate must be exactly 8% or 10%");
        }
    }

    public static TaxConfig standardEightPercentVat() {
        return new TaxConfig(VAT_8_PERCENT);
    }

    public static TaxConfig standardTenPercentVat() {
        return new TaxConfig(VAT_10_PERCENT);
    }

    /** Reads the legally configured rate from -Dhotel.vat.rate or HOTEL_VAT_RATE. */
    public static TaxConfig fromRuntimeConfiguration() {
        String configured = System.getProperty("hotel.vat.rate");
        if (configured == null || configured.isBlank()) configured = System.getenv("HOTEL_VAT_RATE");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Configure hotel.vat.rate or HOTEL_VAT_RATE as 0.08 or 0.10");
        }
        try {
            return new TaxConfig(new BigDecimal(configured.trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("VAT rate must be configured as 0.08 or 0.10", ex);
        }
    }

    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getServiceChargeRate() { return LUXURY_SERVICE_CHARGE; }
    private static BigDecimal validateRate(BigDecimal rate, String label) {
        Objects.requireNonNull(rate, label);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1 inclusive");
        }
        return rate;
    }
}
