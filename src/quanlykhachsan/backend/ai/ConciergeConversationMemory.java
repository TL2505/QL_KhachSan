package quanlykhachsan.backend.ai;

import java.time.Clock;
import java.util.*;

/** Bounded, process-local context. Keys are already bound to the authenticated user. */
final class ConciergeConversationMemory {
    private static final long TTL_MILLIS = 30 * 60_000L;
    private static final int MAX_SESSIONS = 1000, MAX_TURNS = 6, MAX_CHARACTERS = 32_000;
    private final Clock clock;
    private final Map<String, State> sessions = new HashMap<>();
    private static final class State {
        final String configuration;
        final List<CloudAiClient.Turn> turns = new ArrayList<>();
        boolean busy;
        long touched;
        State(String configuration, long now) { this.configuration = configuration; touched = now; }
    }
    static final class Busy extends RuntimeException { }
    ConciergeConversationMemory() { this(Clock.systemUTC()); }
    ConciergeConversationMemory(Clock clock) { this.clock = clock; }
    synchronized Exchange open(String session, AiProviderSettings.Configuration config) {
        long now = clock.millis();
        sessions.entrySet().removeIf(entry -> !entry.getValue().busy && now - entry.getValue().touched >= TTL_MILLIS);
        // Never retain the API key as part of a map key, logs, or browser response.
        String fingerprint = AiInteractionDao.hash(new com.google.gson.Gson().toJson(config));
        State state = sessions.get(session);
        if (state != null && state.busy) throw new Busy();
        if (state == null || !state.configuration.equals(fingerprint)) {
            if (state == null && sessions.size() >= MAX_SESSIONS) {
                var oldest = sessions.entrySet().stream().filter(entry -> !entry.getValue().busy).min(Comparator.comparingLong(entry -> entry.getValue().touched));
                if (oldest.isEmpty()) throw new Busy();
                sessions.remove(oldest.get().getKey());
            }
            state = new State(fingerprint, now); sessions.put(session, state);
        }
        state.busy = true; state.touched = now;
        return new Exchange(session, state);
    }
    synchronized void forget(String session) { sessions.remove(session); }
    synchronized void clear() { sessions.clear(); }
    final class Exchange implements AutoCloseable {
        private final String session;
        private final State state;
        private final List<CloudAiClient.Turn> previous;
        private boolean closed;
        private Exchange(String session, State state) { this.session = session; this.state = state; previous = List.copyOf(state.turns); }
        List<CloudAiClient.Turn> previous() { return previous; }
        void commit(String question, String answer) {
            commit(question, answer, null);
        }
        void commit(String question, String answer, com.google.gson.JsonObject modelContent) {
            synchronized (ConciergeConversationMemory.this) {
                if (closed || sessions.get(session) != state) return;
                state.turns.add(new CloudAiClient.Turn(question, answer, modelContent));
                while (state.turns.size() > MAX_TURNS || characters(state.turns) > MAX_CHARACTERS) state.turns.remove(0);
                state.touched = clock.millis();
            }
        }
        @Override public void close() {
            synchronized (ConciergeConversationMemory.this) {
                if (closed) return;
                closed = true; state.busy = false; state.touched = clock.millis();
                if (state.turns.isEmpty() && sessions.get(session) == state) sessions.remove(session);
            }
        }
    }
    private static int characters(List<CloudAiClient.Turn> turns) { return turns.stream().mapToInt(turn -> turn.question().length() + turn.answer().length() + (turn.modelContent() == null ? 0 : turn.modelContent().toString().length())).sum(); }
}
