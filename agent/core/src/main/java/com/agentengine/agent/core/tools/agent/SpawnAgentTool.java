package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartChildResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.SelfCommand.StartChildCommand;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.utils.AgentUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.agent.infra.utils.ToolUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
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

  private static final Schema NOTEBOOK_GRANTS_SCHEMA =
      ToolUtils.buildSchemaFromType(
              new TypeReference<List<NotebookGrants.NotebookGrant>>() {}.getType())
          .toBuilder()
          .description(
              "Notebook-wide access to grant the spawned agent — lets it add new notes anywhere in the given notebooks. Optional.")
          .build();

  private static final Schema NOTE_GRANTS_SCHEMA =
      ToolUtils.buildSchemaFromType(
              new TypeReference<List<NotebookGrants.NoteGrant>>() {}.getType())
          .toBuilder()
          .description(
              "Read or edit permission to grant the spawned agent for specific, already-existing notes. Optional.")
          .build();

  private final List<String> subAgentIds;
  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;

  public SpawnAgentTool(
      final ActorSystemProvider actorSystemProvider,
      final List<String> subAgentIds,
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository) {
    super(DESCRIPTOR, actorSystemProvider);
    this.subAgentIds = List.copyOf(subAgentIds);
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    if (subAgentIds.isEmpty()) {
      return super.declaration();
    }
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
    properties.put(
        Constants.ToolArgs.KNOWLEDGE_IDS,
        Schema.builder()
            .type(Known.ARRAY)
            .items(Schema.builder().type(Known.STRING).build())
            .description(
                """
                Ids of knowledge you have access to. Grants the spawned agent the same ability to search them with %s. Not knowledge sources — those go in %s instead. Optional.\
                """
                    .formatted(
                        Constants.ToolNames.SEARCH_KNOWLEDGE, Constants.ToolArgs.KNOWLEDGE_SOURCES))
            .build());
    properties.put(
        Constants.ToolArgs.KNOWLEDGE_SOURCES,
        Schema.builder()
            .type(Known.ARRAY)
            .items(Schema.builder().type(Known.STRING).build())
            .description(
                """
                Knowledge sources you have access to. Grants the spawned agent the same ability to read them in full with %s. Not knowledge ids — those go in %s instead. Optional.\
                """
                    .formatted(
                        Constants.ToolNames.READ_KNOWLEDGE_SOURCE,
                        Constants.ToolArgs.KNOWLEDGE_IDS))
            .build());
    properties.put(Constants.ToolArgs.NOTEBOOK_GRANTS, NOTEBOOK_GRANTS_SCHEMA);
    properties.put(Constants.ToolArgs.NOTE_GRANTS, NOTE_GRANTS_SCHEMA);
    final Schema params =
        Schema.builder()
            .type(Known.OBJECT)
            .properties(properties)
            .required(List.of("agent_id", "message", "goal"))
            .build();
    return Optional.of(
        FunctionDeclaration.builder()
            .name(Constants.ToolNames.SPAWN_AGENT)
            .description(DESCRIPTOR.description() + " Available agents: " + agentList + ".")
            .parameters(params)
            .build());
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(name = "agent_id") final String childAgentId,
      @ToolSchema(name = "message") String message,
      @ToolSchema(name = "goal") final String goal,
      @ToolSchema(name = "await_completion", optional = true) Boolean awaitCompletion,
      @ToolSchema(name = Constants.ToolArgs.KNOWLEDGE_IDS, optional = true)
          final List<String> knowledgeIds,
      @ToolSchema(name = Constants.ToolArgs.KNOWLEDGE_SOURCES, optional = true)
          final List<String> knowledgeSources,
      @ToolSchema(name = Constants.ToolArgs.NOTEBOOK_GRANTS, optional = true)
          final List<NotebookGrants.NotebookGrant> notebookGrants,
      @ToolSchema(name = Constants.ToolArgs.NOTE_GRANTS, optional = true)
          final List<NotebookGrants.NoteGrant> noteGrants) {

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
    final ResourceGrants resourceGrants =
        AgentUtils.buildResourceGrants(knowledgeIds, knowledgeSources, notebookGrants, noteGrants);
    final ToolOutput<Map<String, Object>> violationOutput =
        validateGrants(notebookGrants, noteGrants, notebookRepository, notesRepository);
    if (violationOutput != null) {
      return violationOutput;
    }
    final UserMessage userMessage = new UserMessage(parts, resourceGrants);

    final StartChildResult startChildResult =
        actorRef(toolContext)
            .<StartChildResult>ask(
                replyTo ->
                    new StartChildCommand(
                        childAgentId,
                        new UniqueRecord<>(SessionUtils.newSessionId(childAgentId), userMessage),
                        replyTo),
                SessionActorFactory.ASK_TIMEOUT)
            .toCompletableFuture()
            .join();

    final String childSessionId = startChildResult.sessionId();
    final StartSessionResult result = startChildResult.result();
    return switch (result) {
      case StartSessionResult.Accepted ignored -> {
        awaitCompletion = awaitCompletion == null || awaitCompletion;
        SessionUtils.getSessionState(toolContext.invocationContext())
            .addSpawnedAgentReminder(childSessionId, goal, awaitCompletion);
        if (awaitCompletion) {
          yield awaitChild(toolContext, childSessionId);
        } else {
          yield ToolOutput.direct(Map.of(Constants.ToolArgs.CHILD_SESSION_ID, childSessionId));
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
}
