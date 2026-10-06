package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartChildResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.SelfCommand.StartChildCommand;
import com.agentengine.agent.infra.notebook.NotebookService;
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
          Creates a new subordinate agent session and starts it immediately with an initial message. Use to delegate a self-contained task to a specialised agent, or to run multiple tasks concurrently across independent child sessions. Returns a session identifier before the child has produced any output — the child runs asynchronously. The returned identifier can be used in subsequent calls to deliver follow-up messages or to wait for the result.

          Returns: { child_session_id } on success, or { error } on failure.""",
          Map.of());

  private static final Schema KNOWLEDGE_IDS_SCHEMA =
      ToolUtils.buildSchemaFromType(new TypeReference<List<String>>() {}.getType()).toBuilder()
          .description("Knowledge ids to grant the spawned agent. " + KNOWLEDGES_ACCESS_DESCRIPTION)
          .build();

  private static final Schema NOTEBOOK_IDS_SCHEMA =
      ToolUtils.buildSchemaFromType(new TypeReference<List<String>>() {}.getType()).toBuilder()
          .description(
              "Ids of existing notebooks to grant the spawned agent. "
                  + NOTEBOOKS_ACCESS_DESCRIPTION
                  + " Optional.")
          .build();

  private final List<String> subAgentIds;
  private final FunctionDeclaration declaration;

  public SpawnAgentTool(
      final ActorSystemProvider actorSystemProvider,
      final List<String> subAgentIds,
      final NotebookService notebookService,
      final KnowledgeService knowledgeService,
      final AccessControlService accessControlService) {
    super(DESCRIPTOR, actorSystemProvider, notebookService, knowledgeService, accessControlService);
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
                "If true (the default), the tool will wait for the child agent to finish its run and return the final result. If false, the tool will return immediately after the child has been spawned.")
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
