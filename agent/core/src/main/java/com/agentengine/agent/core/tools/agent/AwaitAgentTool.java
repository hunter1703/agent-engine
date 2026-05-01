package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.commands.SelfCommand.AwaitChildCommand;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Awaits a previously spawned child agent run.
 *
 * <p>If the child has already completed, returns the result immediately. Otherwise this tool
 * requests a lightweight confirmation pause and returns a waiting status to the caller.
 */
@SuppressWarnings("ALL")
public final class AwaitAgentTool extends AbstractAgentTool {

    public static final String CHILD_SESSION_ID = "child_session_id";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            Constants.AWAIT_AGENT_TOOL_NAME,
            "Blocks until a child agent session finishes processing its current message and returns the result. "
                    + "Call when you need the child's output before you can proceed — either to use it as input to "
                    + "the next step, or to confirm successful completion before taking a dependent action. "
                    + "If the child has finished, the result is returned immediately otherwise this tool will await for the child to finish.  "
                    + "Times out after 30 minutes. "
                    + "Returns: { child_session_id, result } on success with output; "
                    + "{ child_session_id, status: \"completed\" } on success without output; "
                    + "{ child_session_id, status: \"failed\", error } on failure.",
            Map.of());

    private static final Duration AWAIT_TIMEOUT = Duration.ofMinutes(30);

    public AwaitAgentTool(final ActorSystemProvider actorSystemProvider) {
        super(DESCRIPTOR, actorSystemProvider, true);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    final ToolContext toolContext,
            @ToolSchema(
                            name = CHILD_SESSION_ID,
                            description =
                                    "The opaque identifier of the child agent session to wait for, as returned when the "
                                            + "session was created or last messaged.")
                    final String childSessionId) {
        final Optional<ToolConfirmation> toolConfirmationOptional = toolContext.toolConfirmation();
        if (toolConfirmationOptional.isPresent()) {
            final ToolConfirmation toolConfirmation = toolConfirmationOptional.get();
            if (toolConfirmation.confirmed()) {
                return (Map<String, Object>) toolConfirmation.payload();
            }
        }
        final RunResult result = actorRef(toolContext)
                .<RunResult>ask(replyTo -> new AwaitChildCommand(childSessionId, replyTo), AWAIT_TIMEOUT)
                .toCompletableFuture()
                .join();

        if (!result.completedRun()) {
            toolContext.requestConfirmation(
                    "Waiting for child agent run to complete.", Map.of("child_session_id", childSessionId));
            return null;
        }
        RunUtils.getOrInitState(toolContext.invocationContext()).removeReminder(childSessionId);
        return buildCompletedResponseMap(childSessionId, result);
    }

    public static Map<String, Object> buildCompletedResponseMap(final String childSessionId, final RunResult result) {
        if (result.isFailure()) {
            return Map.of("child_session_id", childSessionId, "status", "failed", "error", result.failureMessage());
        }

        if (result.output() != null) {
            return Map.of("child_session_id", childSessionId, "result", result.output());
        }

        return Map.of("child_session_id", childSessionId, "status", "completed");
    }
}
