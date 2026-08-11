package com.agentengine.agent.api.services;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.ms.client.MicroService;
import org.reactivestreams.Publisher;

@MicroService("agent")
public interface RuntimeService {

    /**
     * Initialises the session actor and enqueues the message. Returns immediately with the
     * resolved session ID; the agent runs in the background.
     */
    Publisher<SessionEvent> startSession(String agentId, String sessionId, UserMessage userMessage);

    /**
     * Records a confirmation on the session actor. Returns immediately; resumed events are
     * delivered via {@link #subscribeToSession}.
     */
    void confirmSession(String sessionId, Confirmation confirmation);

    /**
     * Returns a publisher that emits committed history, then uncommitted current-turn events,
     * then live events, and completes when the terminal event is received. Safe to call from
     * multiple concurrent subscribers for the same session.
     */
    Publisher<SessionEvent> subscribeToSession(String sessionId, boolean liveOnly);

    void rollbackSession(String sessionId, String runId);
}
