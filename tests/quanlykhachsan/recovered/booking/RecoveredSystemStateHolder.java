package quanlykhachsan.recovered.booking;
/** In-memory state only for recovered Phase 1 HTTP contract fixtures. */
public final class RecoveredSystemStateHolder {
 private static boolean frozen;
 private static java.time.LocalDate date=java.time.LocalDate.now();
 public static void reset(){frozen=false;date=java.time.LocalDate.now();}
 public static boolean isSystemFrozen(){return frozen;}
 public static void setFrozen(boolean value){frozen=value;}
 public static java.time.LocalDate getBusinessDate(){return date;}
 public static java.time.LocalDate getCurrentBusinessDate(){return date;}
 public static void advanceBusinessDate(){date=date.plusDays(1);}
}
