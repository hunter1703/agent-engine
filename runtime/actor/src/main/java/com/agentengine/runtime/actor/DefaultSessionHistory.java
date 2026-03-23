package com.agentengine.runtime.actor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class DefaultSessionHistory implements SessionHistory {

    private final SessionActorFactory sessionActorFactory;

    @Inject
    public DefaultSessionHistory(final SessionActorFactory sessionActorFactory) {
        this.sessionActorFactory = sessionActorFactory;
    }

    @Override
    public List<SessionEvent> events(final String agentId, final String sessionId) {
        return sessionActorFactory.entityRef(agentId, sessionId)
                .ask(SessionActor.Command.GetReplayState::new, ActorUtils.DEFAULT_ASK_TIMEOUT)
                .toCompletableFuture()
                .join()
                .events();
    }
}
