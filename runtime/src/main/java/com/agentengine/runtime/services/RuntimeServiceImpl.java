package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.ActorUtils;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionCommand;
import com.agentengine.runtime.actor.SessionReply;
import com.agentengine.runtime.actor.SessionTopologyFactory;
import com.agentengine.runtime.actor.services.RuntimeService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@Singleton
public class RuntimeServiceImpl implements RuntimeService {

  private static final Logger LOG = LoggerFactory.getLogger(RuntimeServiceImpl.class);

  private final SessionActorFactory sessionActorFactory;

  @Inject
  public RuntimeServiceImpl(final SessionActorFactory sessionActorFactory) {
    this.sessionActorFactory = sessionActorFactory;
  }

  @Override
  public CompletionStage<SessionReply.StartRunResult> startSession(final String agentId, final String sessionId, final String message) {
    LOG.info("Starting session {}:{}", agentId, sessionId);
    final var ref = sessionActorFactory.entityRef(agentId, sessionId);
    final var topology = SessionTopologyFactory.rootTopology(agentId, sessionId);
    return ref.<SessionReply.InitializeResult>ask(replyTo -> new SessionCommand.ExternalCommand.InitializeSession(topology, replyTo),
        ActorUtils.DEFAULT_ASK_TIMEOUT)
        .thenCompose(_ -> ref.<SessionReply.StartRunResult>ask(replyTo -> new SessionCommand.ExternalCommand.StartRun(message, replyTo),
            ActorUtils.DEFAULT_ASK_TIMEOUT))
        .whenComplete((result, ex) -> {
          if (ex != null) {
            LOG.error("Failed to start session {}:{}", agentId, sessionId, ex);
          } else {
            LOG.info("Session {}:{} start result: {}", agentId, sessionId, result);
          }
        });
  }

  @Override
  public CompletionStage<SessionReply.ResumeResult> resumeSession(final String agentId, final String sessionId, final String confirmationId,
      final boolean confirmed, final String answer) {
    LOG.info("Resuming session {}:{} with confirmation '{}'", agentId, sessionId, confirmationId);
    final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(agentId, sessionId);
    final Map<String, Object> confirmationResponse = Map.of("confirmed", confirmed, "answer", answer != null ? answer : "");
    return ref.<SessionReply.ResumeResult>ask(
        replyTo -> new SessionCommand.ExternalCommand.ResumeRun(confirmationId, confirmationResponse, replyTo),
        ActorUtils.DEFAULT_ASK_TIMEOUT).whenComplete((result, ex) -> {
          if (ex != null) {
            LOG.error("Failed to resume session {}:{}", agentId, sessionId, ex);
          } else {
            LOG.info("Session {}:{} resume result: {}", agentId, sessionId, result);
          }
        });
  }
}
