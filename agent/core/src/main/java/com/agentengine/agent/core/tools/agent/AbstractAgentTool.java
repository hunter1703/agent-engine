package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.SessionActor;
import com.agentengine.agent.core.session.commands.SelfCommand.AwaitChildCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.agent.infra.utils.ToolUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractAgentTool extends Tool {

  public static final String CHILD_SESSION_ID = "child_session_id";

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAgentTool.class);
  protected final ActorSystemProvider actorSystemProvider;

  protected AbstractAgentTool(
      final ToolDescriptor toolDescriptor, final ActorSystemProvider actorSystemProvider) {
    super(toolDescriptor, true);
    this.actorSystemProvider = actorSystemProvider;
  }

  protected EntityRef<SessionCommand> actorRef(final ToolContext toolContext) {
    return actorSystemProvider.entityRefFor(
        SessionActor.TYPE_KEY, ToolUtils.sessionId(toolContext));
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
          "Waiting for child agent run to complete.", Map.of(CHILD_SESSION_ID, childSessionId));
      LOGGER.info("Awaiting child session {} to complete.", childSessionId);
      return ToolOutput.empty();
    } else {
      LOGGER.info("Child session {} completed with result: {}", childSessionId, result);
    }
    RunUtils.getOrInitState(toolContext.invocationContext()).removeReminder(childSessionId);
    return ToolOutput.direct(AwaitAgentTool.buildCompletedResponseMap(childSessionId, result));
  }

  protected ToolOutput<Map<String, Object>> getResultIfCompleted(final ToolContext toolContext) {
    final Optional<ToolConfirmation> toolConfirmationOptional = toolContext.toolConfirmation();
    if (toolConfirmationOptional.isPresent()) {
      final ToolConfirmation toolConfirmation = toolConfirmationOptional.get();
      if (toolConfirmation.confirmed()) {
        //noinspection unchecked
        return ToolOutput.direct((Map<String, Object>) toolConfirmation.payload());
      }
    }
    return null;
  }
}
