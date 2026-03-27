package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/**
 * Immutable session topology established once at actor initialization.
 * Determines how this session relates to the root of the agent graph.
 */
public record SessionTopology(
        String sessionId,
        String agentId,
        SessionRole role
) implements PekkoSerializable {

    public String rootSessionId() {
        return switch (role) {
            case SessionRole.Root _ -> sessionId;
            case SessionRole.Child c -> c.rootSessionId();
        };
    }

    public boolean isRoot() {
        return role instanceof SessionRole.Root;
    }

    public sealed interface SessionRole extends PekkoSerializable permits SessionRole.Root, SessionRole.Child {
        record Root() implements SessionRole {}

        record Child(
                String rootSessionId,
                String parentSessionId,
                String parentAgentId
        ) implements SessionRole {
            public Child {
                if (rootSessionId == null || parentSessionId == null || parentAgentId == null) {
                    throw new IllegalArgumentException("Child topology fields must not be null");
                }
            }
        }
    }
}
