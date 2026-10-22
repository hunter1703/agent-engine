package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.SelfCommand.SendMessageCommand;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

/**
 * Sends a new message to an already-spawned child agent session, preserving its conversation
 * history.
 *
 * <p>Unlike {@link SpawnAgentTool}, this does not create a new session — the child retains full
 * context from all prior interactions. The child must have completed its previous message (i.e. be
 * IDLE) before a new one can be sent. Use {@link AwaitAgentTool} to confirm completion before
 * calling this.
 *
 * <p>This tool is injected per-run by the framework; it is not a CDI singleton.
 */
public final class SendMessageTool extends DelegationTool {

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.SEND_MESSAGE,
          """
          Delivers a follow-up message to a child you spawned, which keeps its whole conversation. \
          Use it to continue the child's work, add information or access, or correct it. The child \
          must have finished its previous turn.
          Returns: { child_session_id, result } when you wait and the child produced output, { child_session_id, status: "completed" } when it finished without output, { child_session_id, error } if it failed, { status: "started" } when you do not wait, or { error } if the message could not be delivered.""",
          Map.of());

  public SendMessageTool(
      final ActorSystemProvider actorSystemProvider,
      final NotebookService notebookService,
      final KnowledgeService knowledgeService,
      final AccessControlService accessControlService,
      final ReminderSyncService reminderSyncService) {
    super(
        DESCRIPTOR,
        actorSystemProvider,
        notebookService,
        knowledgeService,
        accessControlService,
        reminderSyncService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.CHILD_SESSION_ID,
              description =
                  "The id of the child, as returned when you spawned it.")
          final String childSessionId,
      @ToolArg(
              name = "message",
              description =
                  "The follow-up message.")
          final String message,
      @ToolArg(name = Constants.ToolArgs.GOAL, description = GOAL_SCHEMA_DESCRIPTION)
          final String goal,
      @ToolArg(
              name = Constants.ToolArgs.AWAIT_COMPLETION,
              description =
                  "If true (the default), waits for the child to finish and returns its result; if false, returns as soon as the child has the message.",
              optional = true)
          final Boolean awaitCompletion,
      @ToolArg(
              name = Constants.ToolArgs.KNOWLEDGE_IDS,
              description =
                  "Ids of knowledge to grant the child on top of what it has; list only new ones. "
                      + KNOWLEDGES_ACCESS_DESCRIPTION,
              optional = true)
          final List<String> knowledgeIds,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_IDS,
              description =
                  "Ids of notebooks to grant the child on top of what it has; list only new ones. "
                      + NOTEBOOKS_ACCESS_DESCRIPTION,
              optional = true)
          final List<String> notebookIds) {

    final ToolOutput<Map<String, Object>> completedResult = getResultIfCompleted(toolContext);
    if (completedResult != null) {
      return completedResult;
    }

    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final String childAgentId = sessionState.spawnedAgentId(childSessionId);
    if (childAgentId == null) {
      return ToolOutput.direct(
          Map.of("error", "Unknown child session: " + childSessionId + ". Spawn an agent first."));
    }
    final UserMessage userMessage =
        new UserMessage(List.of(new MessagePart.TextPart(buildFullMessage(goal, message))));
    try {
      grantAccess(childAgentId, childSessionId, notebookIds, knowledgeIds);
    } catch (final GrantRefused refused) {
      return ToolOutput.direct(Map.of("error", refused.getMessage()));
    }

    final StartSessionResult result =
        actorRef(toolContext)
            .<StartSessionResult>ask(
                replyTo ->
                    new SendMessageCommand(
                        childSessionId, new UniqueRecord<>(userMessage), replyTo),
                SessionActorFactory.ASK_TIMEOUT)
            .toCompletableFuture()
            .join();
    final boolean await = waitsForChild(awaitCompletion);
    return afterDelivery(
        toolContext,
        childSessionId,
        result,
        await,
        () -> sessionState.updateSpawnedAgent(childSessionId, goal, await),
        Map.of("status", "started"),
        "Failed to send message");
  }
}
