package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.SessionActor;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.commands.SelfCommand.AwaitChildCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolUtils;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What every tool that works with the child sessions of the calling session shares. */
public abstract class AbstractAgentTool extends Tool {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAgentTool.class);

  private final ActorSystemProvider actorSystemProvider;
  private final ReminderSyncService reminderSyncService;

  protected AbstractAgentTool(
      final ToolDescriptor toolDescriptor,
      final ActorSystemProvider actorSystemProvider,
      final ReminderSyncService reminderSyncService) {
    super(toolDescriptor, true);
    this.actorSystemProvider = actorSystemProvider;
    this.reminderSyncService = reminderSyncService;
  }

  protected EntityRef<SessionCommand> actorRef(final ToolContext toolContext) {
    final String sessionId = com.agentengine.agent.infra.utils.ToolUtils.sessionId(toolContext);
    return actorSystemProvider.entityRefFor(
        SessionActor.TYPE_KEY, SessionActorFactory.entityId(sessionId));
  }

  protected ToolOutput<Map<String, Object>> awaitChild(
      final ToolContext toolContext, final String childSessionId) {

    final ToolOutput<Map<String, Object>> completedResult = getResultIfCompleted(toolContext);
    if (completedResult != null) {
      return completedResult;
    }

    final RunResult result =
        actorRef(toolContext)
            .<RunResult>ask(
                replyTo -> new AwaitChildCommand(childSessionId, replyTo), Duration.ofMinutes(30))
            .toCompletableFuture()
            .join();

    if (!result.completedRun()) {
      toolContext.requestConfirmation(
          "Waiting for child agent run to complete.",
          Map.of(Constants.ToolArgs.CHILD_SESSION_ID, childSessionId));
      LOGGER.debug("Awaiting child session {} to complete.", childSessionId);
      return ToolOutput.empty();
    }
    LOGGER.debug("Child session {} completed with result: {}", childSessionId, result);
    SessionUtils.getSessionState(toolContext.invocationContext())
        .markSpawnedAgentAwaited(childSessionId);

    // The child may have touched the reminders, so they are refreshed.
    reminderSyncService.syncAll(SessionUtils.getSessionState(toolContext.invocationContext()));

    return ToolOutput.direct(AwaitAgentTool.buildCompletedResponseMap(childSessionId, result));
  }

  protected ToolOutput<Map<String, Object>> getResultIfCompleted(final ToolContext toolContext) {
    final Optional<ToolConfirmation> toolConfirmationOptional = toolContext.toolConfirmation();
    if (toolConfirmationOptional.isPresent()) {
      final ToolConfirmation toolConfirmation = toolConfirmationOptional.get();
      if (toolConfirmation.confirmed()
          && ToolUtils.waitsOnChild(name(), toolConfirmation.payload())) {
        //noinspection unchecked
        return ToolOutput.direct((Map<String, Object>) toolConfirmation.payload());
      }
    }
    return null;
  }
}
