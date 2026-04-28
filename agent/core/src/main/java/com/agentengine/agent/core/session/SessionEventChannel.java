package com.agentengine.agent.core.session;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.events.PekkoEventChannel;
import jakarta.inject.Singleton;

/**
 * Scope-keyed channel for live session events where each {@code rootSessionId} maps to an
 * independent stream.
 */
@Singleton
public final class SessionEventChannel extends PekkoEventChannel<String, SessionEvent> {

    public SessionEventChannel(final ActorSystemProvider actorSystemProvider) {
        super(actorSystemProvider, "session-events");
    }
}
