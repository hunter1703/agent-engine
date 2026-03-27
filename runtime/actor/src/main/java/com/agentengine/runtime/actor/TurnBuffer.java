package com.agentengine.runtime.actor;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded mutable buffer for in-flight turn events.
 *
 * <p>Ephemeral actor field — NOT part of SessionState, NOT included in snapshots.
 * Reconstructed empty on recovery. Uncommitted events are intentionally lost
 * on crash; committed chunks are safe in the journal via TurnCommitted facts.
 *
 * <p>When add() returns true, the buffer has reached capacity and should be
 * flushed immediately via drain() + persist(TurnCommitted).
 */
public final class TurnBuffer {

    private static final int DEFAULT_CAPACITY = 500;

    private final List<SessionEvent> events;
    private final int capacity;

    private TurnBuffer(final List<SessionEvent> events, final int capacity) {
        this.events = events;
        this.capacity = capacity;
    }

    public static TurnBuffer create() {
        return new TurnBuffer(new ArrayList<>(), DEFAULT_CAPACITY);
    }

    public static TurnBuffer create(final int capacity) {
        return new TurnBuffer(new ArrayList<>(), capacity);
    }

    /** Adds an event. Returns true if the buffer is now at capacity and should be flushed. */
    public boolean add(final SessionEvent event) {
        events.add(event);
        return events.size() >= capacity;
    }

    /** Returns a snapshot of all buffered events and clears the buffer. */
    public List<SessionEvent> drain() {
        final var snapshot = List.copyOf(events);
        events.clear();
        return snapshot;
    }

    public boolean isEmpty() { return events.isEmpty(); }
    public int size() { return events.size(); }
}
