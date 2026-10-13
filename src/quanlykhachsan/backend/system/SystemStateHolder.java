package quanlykhachsan.backend.system;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-wide operational freeze state shared by the audit engine and mutation filter. */
public final class SystemStateHolder {
    private static final AtomicBoolean FROZEN = new AtomicBoolean();
    private SystemStateHolder() { }
    public static boolean isFrozen() { return FROZEN.get(); }
    public static void setFrozen(boolean frozen) { FROZEN.set(frozen); }
}
