package com.agentengine.runtime.tools.orchestration;

import com.agentengine.runtime.actor.SessionActor;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;

import java.time.Duration;
import java.util.Map;

/**
 * Blocks until a previously spawned child agent session completes and returns its result.
 * <p>
 * Race-safe: terminal child results are cached by the parent actor, so late or retried
 * await calls can still return after the child has already completed or failed.
 */
public final class AwaitAgentTool extends AbstractAgentTool {
    private static final Duration AWAIT_TIMEOUT = Duration.ofMinutes(30);

    public static final String TOOL_NAME = "await_agent";

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Wait for a previously spawned child agent to complete and return its result. " +
            "Blocks until the child finishes or the timeout is reached.",
            Map.of());

    private static final Duration ASK_TIMEOUT = Duration.ofMinutes(10);

    public AwaitAgentTool(final SessionActorFactory actorFactory) {
        super(DESCRIPTOR, actorFactory, true);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
            final ToolContext toolContext,
            @ToolSchema(name = "child_session_id", description = "Session ID returned by spawn_agent")
            final String childSessionId) {
        final SessionActor.ChildResult result = actorRef(toolContext)
                .<SessionActor.ChildResult>ask(
                        replyTo -> new SessionActor.Command.AwaitChildResult(childSessionId, replyTo), AWAIT_TIMEOUT
                        )
                .toCompletableFuture()
                .join();

        return switch (result) {
            case SessionActor.ChildResult.Completed(String id, String answer, var finishReason) ->
                    answer != null
                            ? Map.of("child_session_id", id, "result", answer)
                            : Map.of("child_session_id", id, "status", "completed");
            case SessionActor.ChildResult.Failed(String id, String reason) ->
                    Map.of("child_session_id", id, "error", reason);
        };
    }
}
