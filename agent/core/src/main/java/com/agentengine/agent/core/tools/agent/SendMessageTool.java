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
public final class SendMessageTool extends AbstractAgentTool {

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.SEND_MESSAGE,
          """
          Sends a follow-up message to an ongoing child agent session, allowing you to have a back-and-forth dialogue while preserving its full conversation history. Use this to continue a complex task, provide missing information, grant additional access, or issue corrections to a session that you previously spawned. The child session must have already finished processing its previous message before a new one is accepted; sending to an active session will be rejected. A successful response confirms the message was accepted and the child has begun processing — it does not mean the child has finished.

          Returns: { child_session_id } on success, or { error } on failure.""",
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
                  "The opaque identifier of an existing child agent session to deliver the message to.")
          final String childSessionId,
      @ToolArg(
              name = "message",
              description =
                  "The message content to deliver as the next conversation turn to the child session.")
          String message,
      @ToolArg(name = Constants.ToolArgs.GOAL, description = GOAL_SCHEMA_DESCRIPTION)
          final String goal,
      @ToolArg(
              name = Constants.ToolArgs.AWAIT_COMPLETION,
              description =
                  "If true (the default), the tool will wait for the child agent to finish its run and return the final result. If false, the tool will return immediately after the child has been sent the message.",
              optional = true)
          Boolean awaitCompletion,
      @ToolArg(
              name = Constants.ToolArgs.KNOWLEDGE_IDS,
              description =
                  "Knowledge ids to grant the child, on top of whatever it already has from earlier calls — grants accumulate, so omit ids it can already access and list only new ones. "
                      + KNOWLEDGES_ACCESS_DESCRIPTION,
              optional = true)
          final List<String> knowledgeIds,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_IDS,
              description =
                  "Ids of existing notebooks to grant the child, on top of whatever it already has from earlier calls — grants accumulate, so omit ids it can already access and list only new ones. "
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
    message = buildFullMessage(goal, message);
    final List<MessagePart> parts = List.of(new MessagePart.TextPart(message));
    final ToolOutput<Map<String, Object>> violationOutput =
        validateGrants(notebookIds, knowledgeIds);
    if (violationOutput != null) {
      return violationOutput;
    }
    final UserMessage userMessage = new UserMessage(parts);
    final ToolOutput<Map<String, Object>> grantError =
        issueGrants(childAgentId, childSessionId, notebookIds, knowledgeIds);
    if (grantError != null) {
      return grantError;
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
    return switch (result) {
      case StartSessionResult.Accepted ignored -> {
        awaitCompletion = awaitCompletion == null || awaitCompletion;
        sessionState.updateSpawnedAgent(childSessionId, goal, awaitCompletion);
        if (awaitCompletion) {
          yield awaitChild(toolContext, childSessionId);
        } else {
          yield ToolOutput.direct(Map.of("child_session_id", childSessionId, "status", "started"));
        }
      }
      case StartSessionResult.Rejected(String reason) ->
          ToolOutput.direct(Map.of("error", "Failed to send message: " + reason));
      case StartSessionResult.Queued(int position) ->
          ToolOutput.direct(
              Map.of(
                  "error",
                  "Failed to send message: unexpected queued response",
                  "queue_position",
                  position));
      default -> ToolOutput.direct(Map.of("error", "Failed to send message: unknown response"));
    };
  }
}
