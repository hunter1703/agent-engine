package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartChildResult;
import com.agentengine.agent.core.session.commands.SelfCommand.StartChildCommand;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.utils.SessionState;
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
public final class SpawnAgentTool extends DelegationTool {

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.SPAWN_AGENT,
          """
          Starts a child agent on a message. The child works in its own context and sees only the \
          message and the notebooks and knowledge you grant it. To continue with a child you already \
          have, send it a message instead.
          Returns: { child_session_id, result } when you wait and the child produced output, { child_session_id, status: "completed" } when it finished without output, { child_session_id, error } if it failed, { child_session_id, status: "started" } when you do not wait, or { error } if it could not be started.""",
          Map.of());

  private static final Schema KNOWLEDGE_IDS_SCHEMA =
      ToolUtils.buildSchemaFromType(new TypeReference<List<String>>() {}.getType()).toBuilder()
          .description(
              "Ids of knowledge to grant the child. It sees none of yours unless you pass it here, "
                  + "and it works better from the source than from your paraphrase of it.")
          .build();

  private static final Schema NOTEBOOK_IDS_SCHEMA =
      ToolUtils.buildSchemaFromType(new TypeReference<List<String>>() {}.getType()).toBuilder()
          .description(
              "Ids of notebooks to grant the child, including a shared workspace if the team uses "
                  + "one. A child without the notebooks it needs carries on with incomplete "
                  + "context without saying so.")
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
      @ToolArg(name = "message") final String message,
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
    final UserMessage userMessage =
        new UserMessage(List.of(new MessagePart.TextPart(buildFullMessage(goal, message))));
    final String childSessionId = SessionUtils.newSessionId();
    try {
      // Granted before the child starts, so its first run can already reach them.
      grantAccess(childAgentId, childSessionId, notebookIds, knowledgeIds);
    } catch (final GrantRefused refused) {
      return ToolOutput.direct(Map.of("error", refused.getMessage()));
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

    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final boolean await = waitsForChild(awaitCompletion);
    return afterDelivery(
        toolContext,
        childSessionId,
        startChildResult.result(),
        await,
        () -> sessionState.addSpawnedAgent(childSessionId, childAgentId, goal, await),
        Map.of(Constants.ToolArgs.CHILD_SESSION_ID, childSessionId, "status", "started"),
        "Failed to spawn agent");
  }

  private static FunctionDeclaration buildDeclaration(final List<String> subAgentIds) {
    final Map<String, Schema> properties = new LinkedHashMap<>();
    properties.put(
        "agent_id",
        Schema.builder()
            .type(Known.STRING)
            .enum_(subAgentIds)
            .description("The id of the agent to start.")
            .build());
    properties.put(
        "message",
        Schema.builder()
            .type(Known.STRING)
            .description("The message to start the child with.")
            .build());
    properties.put(
        Constants.ToolArgs.GOAL,
        Schema.builder().type(Known.STRING).description(GOAL_SCHEMA_DESCRIPTION).build());
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
        .description(DESCRIPTOR.description())
        .parameters(params)
        .build();
  }
}
