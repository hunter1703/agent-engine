package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.SelfCommand.SendMessageCommand;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.utils.AgentUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
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
          Sends a follow-up message to an existing child agent session, preserving its full conversation history. Use when the child has completed its previous task but its accumulated context is still relevant — for example, to give corrections, additional instructions, or a new related request without starting a fresh session. The child session must have already finished processing its previous message before a new one is accepted; sending to an active session will be rejected. A successful response confirms the message was accepted and the child has begun processing — it does not mean the child has finished.

          Returns: { child_session_id } on success, or { error } on failure.""",
          Map.of());

  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;

  public SendMessageTool(
      final ActorSystemProvider actorSystemProvider,
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository) {
    super(DESCRIPTOR, actorSystemProvider);
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(
              name = Constants.ToolArgs.CHILD_SESSION_ID,
              description =
                  "The opaque identifier of an existing child agent session to deliver the message to.")
          final String childSessionId,
      @ToolSchema(
              name = "message",
              description =
                  "The message content to deliver as the next conversation turn to the child session.")
          String message,
      @ToolSchema(name = Constants.ToolArgs.GOAL, description = GOAL_SCHEMA_DESCRIPTION)
          final String goal,
      @ToolSchema(
              name = Constants.ToolArgs.AWAIT_COMPLETION,
              description =
                  "If true (the default), the tool will wait for the child agent to finish its run and return the final result. If false, the tool will return immediately after the child has been sent the message.",
              optional = true)
          Boolean awaitCompletion,
      @ToolSchema(
              name = Constants.ToolArgs.KNOWLEDGE_IDS,
              description =
                  """
                  Ids of knowledge to grant the child, on top of whatever it already has from earlier calls — grants accumulate, so omit ids it can already search and list only new ones. Grants the same ability to search them with """
                      + Constants.ToolNames.SEARCH_KNOWLEDGE
                      + """
                      . Not knowledge sources — those go in """
                      + Constants.ToolArgs.KNOWLEDGE_SOURCES
                      + " instead.",
              optional = true)
          final List<String> knowledgeIds,
      @ToolSchema(
              name = Constants.ToolArgs.KNOWLEDGE_SOURCES,
              description =
                  """
                  Knowledge sources to grant the child, on top of whatever it already has from earlier calls — grants accumulate, so omit sources it can already read and list only new ones. Grants the same ability to read them in full with """
                      + Constants.ToolNames.READ_KNOWLEDGE_SOURCE
                      + """
                      . Not knowledge ids — those go in """
                      + Constants.ToolArgs.KNOWLEDGE_IDS
                      + " instead.",
              optional = true)
          final List<String> knowledgeSources,
      @ToolSchema(
              name = Constants.ToolArgs.NOTEBOOK_GRANTS,
              description =
                  """
                  Notebook/note access to grant the child, on top of whatever it already has from earlier calls. Omit anything already granted that isn't changing; repeat an entry only to change its permission (e.g. upgrade a note from read_note to edit_note).""",
              optional = true)
          final List<NotebookGrants.Entry> notebookGrants) {

    final ToolOutput<Map<String, Object>> completedResult = getResultIfCompleted(toolContext);
    if (completedResult != null) {
      return completedResult;
    }

    message = buildFullMessage(goal, message);
    final List<MessagePart> parts = List.of(new MessagePart.TextPart(message));
    final ResourceGrants resourceGrants =
        AgentUtils.buildResourceGrants(knowledgeIds, knowledgeSources, notebookGrants);
    final ToolOutput<Map<String, Object>> violationOutput =
        validateGrants(notebookGrants, notebookRepository, notesRepository);
    if (violationOutput != null) {
      return violationOutput;
    }
    final UserMessage userMessage = new UserMessage(parts, resourceGrants);

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
        SessionUtils.getSessionState(toolContext.invocationContext())
            .addSpawnedAgentReminder(childSessionId, goal, awaitCompletion);
        if (awaitCompletion) {
          yield awaitChild(toolContext, childSessionId);
        } else {
          yield ToolOutput.direct(Map.of("child_session_id", childSessionId));
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
