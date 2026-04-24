package com.agentengine.runtime.tools.agent;

import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.session.StartChildResult;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.runtime.session.commands.SelfCommand.StartChildCommand;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
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

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Creates a new subordinate agent session and starts it immediately with an initial message. "
                    + "Use to delegate a self-contained task to a specialised agent, or to run multiple tasks "
                    + "concurrently across independent child sessions. Returns a session identifier before the child "
                    + "has produced any output — the child runs asynchronously. The returned identifier can be used "
                    + "in subsequent calls to deliver follow-up messages or to wait for the result. "
                    + "Returns: { child_session_id } on success, or { error } on failure.",
            Map.of());

    private final List<String> subAgentIds;

    public SpawnAgentTool(final ActorSystemProvider actorSystemProvider, final List<String> subAgentIds) {
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
                        .type("STRING")
                        .enum_(subAgentIds)
                        .description("ID of the agent to spawn. Available agents: " + agentList + ". Required.")
                        .build());
        properties.put(
                "message",
                Schema.builder()
                        .type("STRING")
                        .description("Initial message to send to the spawned agent. Required.")
                        .build());
        final Schema params = Schema.builder()
                .type("OBJECT")
                .properties(properties)
                .required(List.of("agent_id", "message"))
                .build();
        return Optional.of(FunctionDeclaration.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTOR.description() + " Available agents: " + agentList + ".")
                .parameters(params)
                .build());
    }

    public Map<String, Object> execute(
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
                    final String message) {
        final StartChildResult startChildResult = actorRef(toolContext)
                .<StartChildResult>ask(
                        replyTo -> new StartChildCommand(childAgentId, new UniqueRecord<>(message), replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .toCompletableFuture()
                .join();

        final String childSessionId = startChildResult.sessionId();
        final StartSessionResult result = startChildResult.result();
        return switch (result) {
            case StartSessionResult.Accepted ignored -> Map.of("child_session_id", childSessionId);
            case StartSessionResult.Rejected(String reason) -> Map.of("error", "Failed to spawn agent: " + reason);
            case StartSessionResult.Queued(int position) ->
                Map.of("error", "Failed to spawn agent: unexpected queued response", "queue_position", position);
            default -> Map.of("error", "Failed to spawn agent: unknown response");
        };
    }
}
