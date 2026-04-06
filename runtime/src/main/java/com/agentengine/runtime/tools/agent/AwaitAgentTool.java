package com.agentengine.runtime.tools.agent;

import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.session.commands.ExternalCommand;
import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.tools.ToolContext;
import java.time.Duration;
import java.util.Map;

/**
 * Awaits a previously spawned child agent run.
 *
 * <p>If the child has already completed, returns the result immediately. Otherwise this tool
 * requests a lightweight confirmation pause and returns a waiting status to the caller.
 */
public final class AwaitAgentTool extends AbstractAgentTool {

    public static final String TOOL_NAME = "await_agent";

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Wait for a previously spawned child agent run to complete and return its result. "
                    + "Blocks until the child finishes or the timeout is reached.",
            Map.of());

    private static final Duration AWAIT_TIMEOUT = Duration.ofMinutes(30);

    public AwaitAgentTool(final ActorSystemProvider actorSystemProvider) {
        super(DESCRIPTOR, actorSystemProvider);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    final ToolContext toolContext,
            @ToolSchema(name = "child_session_id", description = "Session ID returned by spawn_agent or send_message")
                    final String childSessionId) {
        final RunResult result = actorRef(toolContext)
                .<RunResult>ask(replyTo -> new ExternalCommand.AwaitCommand(childSessionId, replyTo), AWAIT_TIMEOUT)
                .toCompletableFuture()
                .join();

        if (!result.completedRun()) {
            toolContext.requestConfirmation(
                    "Waiting for child agent run to complete.", Map.of("child_session_id", childSessionId));
            return Map.of("child_session_id", childSessionId, "status", "waiting_for_child");
        }

        if (result.isFailure()) {
            return Map.of("child_session_id", childSessionId, "status", "failed", "error", result.failureMessage());
        }

        if (result.output() != null) {
            return Map.of("child_session_id", childSessionId, "result", result.output());
        }

        return Map.of("child_session_id", childSessionId, "status", "completed");
    }
}
