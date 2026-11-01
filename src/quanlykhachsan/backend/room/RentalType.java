package quanlykhachsan.backend.room;

import java.util.Locale;

/** Booking tariff that also selects a room's turnaround buffer. */
public enum RentalType {
    HOURLY("HOURLY", 30),
    OVERNIGHT("OVERNIGHT", 60),
    DAILY("DAILY", 120);

    private final String databaseValue;
    private final int defaultBufferMinutes;

    RentalType(String databaseValue, int defaultBufferMinutes) {
        this.databaseValue = databaseValue;
        this.defaultBufferMinutes = defaultBufferMinutes;
    }

    public String databaseValue() { return databaseValue; }
    public int defaultBufferMinutes() { return defaultBufferMinutes; }

    public static RentalType parse(String value) {
        if (value == null || value.isBlank()) return DAILY;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unsupported rental type"); }
    }
}
