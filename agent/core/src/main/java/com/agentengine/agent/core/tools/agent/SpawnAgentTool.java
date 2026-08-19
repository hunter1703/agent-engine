package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartChildResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.SelfCommand.StartChildCommand;
import com.agentengine.agent.infra.utils.Reminder;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.ActorSystemProvider;
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

  public static final String TOOL_NAME = "spawn_agent";

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Creates a new subordinate agent session and starts it immediately with an initial message. "
              + "Use to delegate a self-contained task to a specialised agent, or to run multiple tasks "
              + "concurrently across independent child sessions. Returns a session identifier before the child "
              + "has produced any output — the child runs asynchronously. The returned identifier can be used "
              + "in subsequent calls to deliver follow-up messages or to wait for the result. "
              + "Returns: { child_session_id } on success, or { error } on failure.",
          Map.of());

  private final List<String> subAgentIds;

  public SpawnAgentTool(
      final ActorSystemProvider actorSystemProvider, final List<String> subAgentIds) {
    super(DESCRIPTOR, actorSystemProvider);
    this.subAgentIds = List.copyOf(subAgentIds);
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
            .description("ID of the agent to spawn. Available agents: " + agentList + ". Required.")
            .build());
    properties.put(
        "message",
        Schema.builder()
            .type(Known.STRING)
            .description("Initial message to send to the spawned agent. Required.")
            .build());
    properties.put(
        "goal",
        Schema.builder()
            .type(Known.STRING)
            .description("The outcome this child agent is expected to deliver. Required.")
            .build());
    properties.put(
        "await_completion",
        Schema.builder()
            .type(Known.BOOLEAN)
            .description(
                "If true (the default), the tool will wait for the child agent to finish its run and return the final result. If false, the tool will return immediately after the child has been spawned.")
            .build());
    final Schema params =
        Schema.builder()
            .type(Known.OBJECT)
            .properties(properties)
            .required(List.of("agent_id", "message", "goal"))
            .build();
    return Optional.of(
        FunctionDeclaration.builder()
            .name(TOOL_NAME)
            .description(DESCRIPTOR.description() + " Available agents: " + agentList + ".")
            .parameters(params)
            .build());
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(
              name = "agent_id",
              description =
                  "Identifier of the agent type to instantiate. Determines the agent's instructions, "
                      + "tools, and behaviour profile. Must be one of the available agents in the current deployment.")
          final String childAgentId,
      @ToolSchema(
              name = "message",
              description =
                  "The first message to deliver to the newly created agent session, framing the task "
                      + "or question the child should work on.")
          final String message,
      @ToolSchema(
              name = "goal",
              description = "The outcome this child agent is expected to deliver.")
          final String goal,
      @ToolSchema(
              name = "await_completion",
              description =
                  "If true (the default), the tool will wait for the child agent to finish its run and return the final result. If false, the tool will return immediately after the child has been spawned.",
              optional = true)
          Boolean awaitCompletion) {

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
    final String completeMessage =
        StringUtils.isNotBlank(goal) ? "Goal: " + goal + "\n\n" + message : message;
    final StartChildResult startChildResult =
        actorRef(toolContext)
            .<StartChildResult>ask(
                replyTo ->
                    new StartChildCommand(
                        childAgentId, new UniqueRecord<>(completeMessage), replyTo),
                SessionActorFactory.ASK_TIMEOUT)
            .toCompletableFuture()
            .join();

    final String childSessionId = startChildResult.sessionId();
    final StartSessionResult result = startChildResult.result();
    return switch (result) {
      case StartSessionResult.Accepted ignored -> {
        awaitCompletion = awaitCompletion == null || awaitCompletion;
        if (awaitCompletion) {
          yield awaitChild(toolContext, childSessionId);
        } else {
          final RunState runState = RunUtils.getOrInitState(toolContext.invocationContext());
          runState.addReminder(
              new Reminder(
                  Reminder.GROUP_SPAWNED_AGENTS,
                  childSessionId,
                  "agent='"
                      + childAgentId
                      + "' goal='"
                      + goal
                      + "' — running asynchronously, not yet awaited. "
                      + "Use "
                      + AwaitAgentTool.DESCRIPTOR.name()
                      + " with child_session_id='"
                      + childSessionId
                      + "' when you need its result."));
          yield ToolOutput.direct(Map.of("child_session_id", childSessionId));
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
