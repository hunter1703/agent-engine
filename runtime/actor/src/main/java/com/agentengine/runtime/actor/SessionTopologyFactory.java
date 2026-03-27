package com.agentengine.runtime.actor;

/** Constructs {@link SessionTopology} values for root and child sessions. */
public final class SessionTopologyFactory {

    private SessionTopologyFactory() {}

    public static SessionTopology rootTopology(final String agentId, final String sessionId) {
        return new SessionTopology(sessionId, agentId, new SessionTopology.SessionRole.Root());
    }

    public static SessionTopology childTopology(final String agentId, final String sessionId,
            final String rootSessionId, final String parentSessionId, final String parentAgentId) {
        return new SessionTopology(sessionId, agentId,
                new SessionTopology.SessionRole.Child(rootSessionId, parentSessionId, parentAgentId));
    }
}
