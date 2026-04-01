package com.agentengine.core.services;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.actor.ResumeResult;
import com.agentengine.runtime.actor.RuntimeService;
import com.agentengine.runtime.actor.SessionEventChannel;
import com.agentengine.runtime.actor.StartSessionResult;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.events.EventSubscription;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.event.BaseEvent;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class AgentExecutionServiceImpl implements AgentExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionServiceImpl.class);

    private final SessionService sessionService;
    private final RuntimeService runtimeService;
    private final SessionEventChannel sessionEventChannel;

    @Inject
    public AgentExecutionServiceImpl(
            final SessionService sessionService,
            final RuntimeService runtimeService,
            final SessionEventChannel sessionEventChannel) {
        this.sessionService = sessionService;
        this.runtimeService = runtimeService;
        this.sessionEventChannel = sessionEventChannel;
    }

    @Override
    public Publisher<BaseEvent> run(final String agentId, final String text) {
        final String sessionId = UUID.randomUUID().toString();
        return Flowable.fromCompletionStage(sessionEventChannel.subscribe(sessionId))
                .flatMap(subscription -> Flowable.just(runtimeService.startSession(agentId, sessionId, text))
                        .flatMap(startResult -> {
                            if (startResult instanceof StartSessionResult.Rejected(String reason)) {
                                cancelQuietly(subscription, "run start rejected for session " + sessionId);
                                return Flowable.error(new IllegalStateException("Run rejected: " + reason));
                            }
                            return Flowable.fromPublisher(mapEvents(agentId, sessionId, subscription));
                        })
                        .doOnError(error -> cancelQuietly(subscription, "run start failed for session " + sessionId)));
    }

    @Override
    public Publisher<BaseEvent> resumeSession(
            final String sessionId, final String confirmationId, final Boolean confirmed, final String answer) {
        final AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
        }

        final String rootSessionId = session.getRootSessionId();
        final String rootAgentId = session.getRootAgentId();

        final ResumeResult resumeStage = runtimeService.resumeSession(
                rootAgentId, rootSessionId, confirmationId, confirmed != null && confirmed, answer);

        return Flowable.fromCompletionStage(sessionEventChannel.subscribe(rootSessionId))
                .flatMap(subscription -> Flowable.just(resumeStage)
                        .flatMap(result -> {
                            if (result instanceof ResumeResult.Rejected(String reason)) {
                                cancelQuietly(subscription, "resume rejected for session " + rootSessionId);
                                return Flowable.error(new IllegalStateException("Resume rejected: " + reason));
                            }
                            return Flowable.fromPublisher(mapEvents(rootAgentId, rootSessionId, subscription));
                        })
                        .doOnError(error -> cancelQuietly(subscription, "resume failed for session " + rootSessionId)));
    }

    private static Publisher<BaseEvent> mapEvents(
            final String agentId,
            final String sessionId,
            final EventSubscription<SequencedEvent<SessionEvent>> subscription) {
        final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, agentId);
        return Flowable.fromPublisher(subscription.publisher())
                .concatMap(event -> mapper.map(event.payload()))
                .takeUntil(mapper::isTerminalEvent)
                .doFinally(() -> cancelQuietly(subscription, null));
    }

    private static void cancelQuietly(
            final EventSubscription<SequencedEvent<SessionEvent>> subscription, final String reason) {
        subscription.cancel().whenComplete((ignored, error) -> {
            if (error != null) {
                LOG.debug("Failed to cancel subscription after {}", reason, error);
            }
        });
    }
}
