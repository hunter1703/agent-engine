package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartChildResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.SelfCommand.StartChildCommand;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.agent.infra.utils.ToolUtils;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type.Known;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spawns a new child agent session and starts it with the given message.
 *
 * <p>The child runs asynchronously. Use {@link AwaitAgentTool} to collect its result, or {@link
 * SendMessageTool} to send follow-up messages while preserving its context. Both tools require the
 * child_session_id returned by this tool. This tool is injected per-run; it is not a CDI singleton.
 */
public final class SpawnAgentTool extends AbstractAgentTool {

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.SPAWN_AGENT,
          """
                  Creates a new child agent session and starts it with the given message. The child has no prior context — it sees only the message you send and the material you explicitly grant. To continue an existing child session, use SendMessageTool instead.

                  Nothing you have access to is automatically available to the child. Pass `notebook_ids` for every notebook the child needs to read or write — including the shared workspace. A child spawned without the notebooks it needs may proceed silently with incomplete context rather than fail.

                  By default the tool waits for the child to finish and returns its result. Set `await_completion` to false to return immediately with just the child session id; use AwaitAgentTool to collect its result later.

                  Returns: { child_session_id, result } if awaited, { child_session_id, status: "started" } if not, or { error } on failure.""",
          Map.of());

  private static final Schema KNOWLEDGE_IDS_SCHEMA =
          ToolUtils.buildSchemaFromType(new TypeReference<List<String>>() {}.getType()).toBuilder()
                  .description(
                          "Knowledge ids to grant the spawned agent. Source material you have access to "
                                  + "is not automatically visible to the child — pass the ids of any documents "
                                  + "or references the child's work depends on. A child working from your "
                                  + "paraphrase of source material produces weaker work than one reading the "
                                  + "source directly. Optional.")
                  .build();

  private static final Schema NOTEBOOK_IDS_SCHEMA =
          ToolUtils.buildSchemaFromType(new TypeReference<List<String>>() {}.getType()).toBuilder()
                  .description(
                          "Ids of notebooks to grant the spawned agent. Notebooks you have access to are "
                                  + "not automatically visible to the child — pass every notebook the child's "
                                  + "work depends on, including the shared workspace if the team is using one. "
                                  + "Optional, but a child spawned without the notebooks it needs may proceed "
                                  + "silently with incomplete context.")
                  .build();

  private final List<String> subAgentIds;
  private final FunctionDeclaration declaration;

  public SpawnAgentTool(
      final ActorSystemProvider actorSystemProvider,
      final List<String> subAgentIds,
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
    this.subAgentIds = List.copyOf(subAgentIds);
    this.declaration = buildDeclaration(this.subAgentIds);
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    return Optional.of(declaration);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(name = Constants.ToolArgs.AGENT_ID) final String childAgentId,
      @ToolArg(name = "message") String message,
      @ToolArg(name = "goal") final String goal,
      @ToolArg(name = "await_completion", optional = true) Boolean awaitCompletion,
      @ToolArg(name = Constants.ToolArgs.KNOWLEDGE_IDS, optional = true)
          final List<String> knowledgeIds,
      @ToolArg(name = Constants.ToolArgs.NOTEBOOK_IDS, optional = true)
          final List<String> notebookIds) {

    final ToolOutput<Map<String, Object>> completedResult = getResultIfCompleted(toolContext);
    if (completedResult != null) {
      return completedResult;
    }

    if (!subAgentIds.contains(childAgentId)) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Invalid agent_id '"
                  + childAgentId
                  + "'. Must be one of: "
                  + String.join(", ", subAgentIds)));
    }
    message = buildFullMessage(goal, message);

    final List<MessagePart> parts = List.of(new MessagePart.TextPart(message));
    final ToolOutput<Map<String, Object>> violationOutput =
        validateGrants(notebookIds, knowledgeIds);
    if (violationOutput != null) {
      return violationOutput;
    }
    final UserMessage userMessage = new UserMessage(parts);
    final String childSessionId = SessionUtils.newSessionId();
    // Granted before the child starts, so its first run can already reach them.
    final ToolOutput<Map<String, Object>> grantError =
        issueGrants(childAgentId, childSessionId, notebookIds, knowledgeIds);
    if (grantError != null) {
      return grantError;
    }

    final StartChildResult startChildResult =
        actorRef(toolContext)
            .<StartChildResult>ask(
                replyTo ->
                    new StartChildCommand(
                        childAgentId, new UniqueRecord<>(childSessionId, userMessage), replyTo),
                SessionActorFactory.ASK_TIMEOUT)
            .toCompletableFuture()
            .join();

    final StartSessionResult result = startChildResult.result();
    return switch (result) {
      case StartSessionResult.Accepted ignored -> {
        awaitCompletion = awaitCompletion == null || awaitCompletion;
        SessionUtils.getSessionState(toolContext.invocationContext())
            .addSpawnedAgent(childSessionId, childAgentId, goal, awaitCompletion);
        if (awaitCompletion) {
          yield awaitChild(toolContext, childSessionId);
        } else {
          yield ToolOutput.direct(
              Map.of(Constants.ToolArgs.CHILD_SESSION_ID, childSessionId, "status", "started"));
        }
      }
      case StartSessionResult.Rejected(String r) ->
          ToolOutput.direct(Map.of("error", "Failed to spawn agent: " + r));
      case StartSessionResult.Queued(int position) ->
          ToolOutput.direct(
              Map.of(
                  "error",
                  "Failed to spawn agent: unexpected queued response",
                  "queue_position",
                  position));
      default -> ToolOutput.direct(Map.of("error", "Failed to spawn agent: unknown response"));
    };
  }

  private static FunctionDeclaration buildDeclaration(final List<String> subAgentIds) {
    final String agentList = String.join(", ", subAgentIds);
    final Map<String, Schema> properties = new LinkedHashMap<>();
    properties.put(
        "agent_id",
        Schema.builder()
            .type(Known.STRING)
            .enum_(subAgentIds)
            .description(
                "ID of the agent to spawn. Available agents: %s. Required.".formatted(agentList))
            .build());
    properties.put(
        "message",
        Schema.builder()
            .type(Known.STRING)
            .description("Initial message to send to the spawned agent. Required.")
            .build());
    properties.put(
        Constants.ToolArgs.GOAL,
        Schema.builder()
            .type(Known.STRING)
            .description(GOAL_SCHEMA_DESCRIPTION + " Required.")
            .build());
    properties.put(
            Constants.ToolArgs.AWAIT_COMPLETION,
            Schema.builder()
                    .type(Known.BOOLEAN)
                    .description(
                            "If true (the default), the tool waits for the child agent to finish and "
                                    + "returns its result. If false, the tool returns immediately with the "
                                    + "child_session_id.")
                    .build());
    properties.put(Constants.ToolArgs.KNOWLEDGE_IDS, KNOWLEDGE_IDS_SCHEMA);
    properties.put(Constants.ToolArgs.NOTEBOOK_IDS, NOTEBOOK_IDS_SCHEMA);
    final Schema params =
        Schema.builder()
            .type(Known.OBJECT)
            .properties(properties)
            .required(List.of("agent_id", "message", "goal"))
            .build();
    return FunctionDeclaration.builder()
        .name(Constants.ToolNames.SPAWN_AGENT)
        .description(DESCRIPTOR.description() + " Available agents: " + agentList + ".")
        .parameters(params)
        .build();
  }
}
