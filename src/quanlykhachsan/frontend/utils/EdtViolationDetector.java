package quanlykhachsan.frontend.utils;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Measures actual Swing event dispatch time and reports handlers exceeding 16ms. */
public final class EdtViolationDetector {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private EdtViolationDetector() { }

    public static void install() { install(message -> System.err.println(message)); }

    public static void install(Consumer<String> reporter) {
        Objects.requireNonNull(reporter, "reporter");
        if (!INSTALLED.compareAndSet(false, true)) return;
        EventQueue queue = new EventQueue() {
            @Override protected void dispatchEvent(AWTEvent event) {
                long start = System.nanoTime();
                try { super.dispatchEvent(event); }
                finally {
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    if (elapsedMs > 16) reporter.accept("EDT dispatch exceeded 16ms: " + elapsedMs + "ms (" + event.getClass().getSimpleName() + ")");
                }
            }
        };
        ToolkitQueue.push(queue);
    }

    private static final class ToolkitQueue {
        private static void push(EventQueue queue) { java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().push(queue); }
    }
}
