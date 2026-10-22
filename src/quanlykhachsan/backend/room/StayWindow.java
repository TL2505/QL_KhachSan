package quanlykhachsan.backend.room;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Validated hotel-local stay interval and its effective rental type. */
public record StayWindow(RentalType rentalType, LocalDateTime checkIn, LocalDateTime checkOut) {
    private static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime DAILY_CHECK_IN = LocalTime.of(14, 0);
    private static final LocalTime DAILY_CHECK_OUT = LocalTime.NOON;
    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    public static StayWindow resolve(RentalType requestedType, LocalDateTime requestedIn, LocalDateTime requestedOut) {
        if (requestedType == null || requestedIn == null || requestedOut == null) {
            throw new IllegalArgumentException("Rental type and stay times are required");
        }
        RentalType effectiveType = requestedType;
        LocalDateTime in = requestedIn;
        LocalDateTime out = requestedOut;

        // A long hourly request is repriced as the package that best matches its time window.
        if (requestedType == RentalType.HOURLY && Duration.between(in, out).compareTo(Duration.ofHours(6)) > 0) {
            if (isOvernightPackageCandidate(in, out)) {
                effectiveType = RentalType.OVERNIGHT;
            } else {
                effectiveType = RentalType.DAILY;
                LocalDate checkoutDay = out.toLocalDate().isAfter(in.toLocalDate())
                        ? out.toLocalDate() : in.toLocalDate().plusDays(1);
                in = in.toLocalDate().atTime(DAILY_CHECK_IN);
                out = checkoutDay.atTime(DAILY_CHECK_OUT);
            }
        }

        if (in.isBefore(LocalDateTime.now(HOTEL_ZONE))) {
            throw new IllegalArgumentException("Check-in time has already passed");
        }
        if (!out.isAfter(in)) throw new IllegalArgumentException("Check-out must follow check-in");

        switch (effectiveType) {
            case DAILY -> {
                long nights = ChronoUnit.DAYS.between(in.toLocalDate(), out.toLocalDate());
                if (!in.toLocalTime().equals(DAILY_CHECK_IN)
                        || !out.toLocalTime().equals(DAILY_CHECK_OUT)
                        || nights < 1 || nights > 365) {
                    throw new IllegalArgumentException("Daily stays use 14:00 check-in and 12:00 check-out");
                }
            }
            case OVERNIGHT -> {
                if (!coversOvernightCore(in, out)) {
                    throw new IllegalArgumentException("Overnight stays must cover 22:00–06:00 on consecutive dates");
                }
            }
            case HOURLY -> {
                Duration duration = Duration.between(in, out);
                if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(6)) > 0) {
                    throw new IllegalArgumentException("Hourly stays must be longer than zero and no longer than six hours");
                }
            }
        }
        return new StayWindow(effectiveType, in, out);
    }

    private static boolean coversOvernightCore(LocalDateTime in, LocalDateTime out) {
        return !in.toLocalTime().isAfter(NIGHT_START)
                && out.toLocalDate().equals(in.toLocalDate().plusDays(1))
                && !out.toLocalTime().isBefore(NIGHT_END);
    }

    private static boolean isOvernightPackageCandidate(LocalDateTime in,LocalDateTime out) {
        return coversOvernightCore(in,out)
                && !in.toLocalTime().isBefore(LocalTime.of(18,0))
                && !out.toLocalTime().isAfter(LocalTime.NOON);
    }

    public LocalDateTime releaseAt(int bufferMinutes) {
        if (bufferMinutes < 0 || bufferMinutes > 1440) throw new IllegalArgumentException("Invalid room buffer");
        return checkOut.plusMinutes(bufferMinutes);
    }

    public long billableHourlyAddOns() {
        if (rentalType == RentalType.HOURLY) {
            long extraSeconds = Math.max(0, Duration.between(checkIn, checkOut).getSeconds() - 7200);
            return (extraSeconds + 3599) / 3600;
        }
        if (rentalType == RentalType.OVERNIGHT) {
            long earlyMinutes = Duration.between(checkIn, checkIn.toLocalDate().atTime(NIGHT_START)).toMinutes();
            long lateMinutes = Duration.between(checkOut.toLocalDate().atTime(NIGHT_END), checkOut).toMinutes();
            return ceilHours(Math.max(0, earlyMinutes)) + ceilHours(Math.max(0, lateMinutes));
        }
        return 0;
    }

    private static long ceilHours(long minutes) { return (minutes + 59) / 60; }
}
