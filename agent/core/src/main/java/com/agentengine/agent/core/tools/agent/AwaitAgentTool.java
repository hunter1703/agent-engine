package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.tools.ToolContext;
import java.util.Map;

/**
 * Awaits a previously spawned child agent run.
 *
 * <p>If the child has already completed, returns the result immediately. Otherwise this tool raises
 * a lightweight interrupt and returns a waiting status to the caller.
 */
@SuppressWarnings("ALL")
public final class AwaitAgentTool extends AbstractAgentTool {

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.AWAIT_AGENT,
          """
          Waits for a child to finish its current message and returns its result; your run pauses \
          until then. If it already finished, the result comes back at once.
          Returns: { child_session_id, result } when the child produced output; { child_session_id, status: "completed" } when it finished without output; { child_session_id, error } when it failed.""",
          Map.of());

  public AwaitAgentTool(
      final ActorSystemProvider actorSystemProvider,
      final ReminderSyncService reminderSyncService) {
    super(DESCRIPTOR, actorSystemProvider, reminderSyncService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.CHILD_SESSION_ID,
              description =
                  "The id of the child, as returned when you spawned it.")
          final String childSessionId) {
    return awaitChild(toolContext, childSessionId);
  }

  public static Map<String, Object> buildCompletedResponseMap(
      final String childSessionId, final RunResult result) {
    if (result.isFailure()) {
      return Map.of("child_session_id", childSessionId, "error", result.failureMessage());
    }

    if (result.output() != null) {
      return Map.of("child_session_id", childSessionId, "result", result.output());
    }

    return Map.of("child_session_id", childSessionId, "status", "completed");
  }
}
