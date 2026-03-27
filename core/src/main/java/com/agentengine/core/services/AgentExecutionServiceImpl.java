package com.agentengine.core.services;

import com.agentengine.core.agui.AGUIEventMapper;
import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.actor.SessionEventChannel;
import com.agentengine.runtime.actor.SessionReply;
import com.agentengine.runtime.actor.services.RuntimeService;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Singleton
public class AgentExecutionServiceImpl implements AgentExecutionService {

  private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionServiceImpl.class);

  private final SessionService sessionService;
  private final RuntimeService runtimeService;
  private final SessionEventChannel sessionEventChannel;

  @Inject
  public AgentExecutionServiceImpl(final SessionService sessionService, final RuntimeService runtimeService,
      final SessionEventChannel sessionEventChannel) {
    this.sessionService = sessionService;
    this.runtimeService = runtimeService;
    this.sessionEventChannel = sessionEventChannel;
  }

  @Override
  public Publisher<BaseEvent> run(final String agentId, final String text) {
    final String sessionId = UUID.randomUUID().toString();
    final Publisher<BaseEvent> publisher = subscribe(agentId, sessionId);
    runtimeService.startSession(agentId, sessionId, text).whenComplete((result, ex) -> {
      if (ex != null) {
        // System failure (actor timeout, cluster down) — the actor cannot clean up; we
        // must.
        LOG.error("Session {}:{} failed to start: {}", agentId, sessionId, ex.getMessage());
        sessionEventChannel.complete(sessionId);
      } else if (result instanceof SessionReply.StartRunResult.Rejected rejected) {
        // Rejection is handled by the actor, which calls eventChannel.complete()
        // itself.
        LOG.error("Session {}:{} start rejected: {}", agentId, sessionId, rejected.reason());
      }
    });
    return publisher;
  }

  @Override
  public Publisher<BaseEvent> resumeSession(final String sessionId, final String confirmationId, final Boolean confirmed,
      final String answer) {
    final AgentSession session = sessionService.getSession(sessionId);
    if (session == null) {
      throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
    }
    // Always resume from the root: the cascade unwinds top-down through the awaited
    // hierarchy.
    final String rootSessionId = session.getRootSessionId();
    final String rootAgentId = session.getRootAgentId();
    // Subscribe before sending the command so no events are missed.
    final Publisher<BaseEvent> channelEvents = subscribe(rootAgentId, rootSessionId);
    return Flowable
        .fromCompletionStage(
            runtimeService.resumeSession(rootAgentId, rootSessionId, confirmationId, confirmed != null && confirmed, answer))
        .concatMap(result -> switch (result) {
          case SessionReply.ResumeResult.Resumed _ -> Flowable.fromPublisher(channelEvents);
          case SessionReply.ResumeResult.Rejected(String reason) -> {
            LOG.error("Session {} resume rejected: {}", rootSessionId, reason);
            yield Flowable.error(new IllegalStateException("Resume rejected: " + reason));
          }
        });
  }

  private Publisher<BaseEvent> subscribe(final String agentId, final String sessionId) {
    final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, agentId);
    return Flowable.fromPublisher(sessionEventChannel.events(sessionId)).concatMap(mapper::map);
  }
}
