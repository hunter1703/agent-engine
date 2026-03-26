package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/**
 * Complete durable state of a session actor.
 * Reconstructed from journal facts on recovery via the event handler.
 * TurnBuffer is NOT part of this state — it is ephemeral and always
 * starts empty after recovery.
 */
public record SessionState(
        SessionTopology topology,
        ExecutionState execution,
        ChildRegistry childRegistry,
        long nextSequence
) implements PekkoSerializable {

    /** Initial state after SessionInitialized fact is applied. */
    public static SessionState initial(final SessionTopology topology) {
        return new SessionState(
                topology,
                new ExecutionState.Idle(MessageQueue.EMPTY),
                ChildRegistry.EMPTY,
                0L
        );
    }

    public SessionState withExecution(final ExecutionState execution) {
        return new SessionState(topology, execution, childRegistry, nextSequence);
    }

    public SessionState withChildRegistry(final ChildRegistry childRegistry) {
        return new SessionState(topology, execution, childRegistry, nextSequence);
    }

    public SessionState withNextSequence(final long nextSequence) {
        return new SessionState(topology, execution, childRegistry, nextSequence);
    }

    // ── Derived convenience accessors ────────────────────────────────────────

    public String rootSessionId()   { return topology.rootSessionId(); }
    public boolean isRoot()         { return topology.isRoot(); }
    public MessageQueue queue()     { return execution.queue(); }
    public boolean isInitialized()  { return topology != null; }
}
