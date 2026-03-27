package com.agentengine.runtime.tools.agent;

import com.agentengine.runtime.actor.ChildRegistry;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionCommand;
import com.agentengine.runtime.actor.SessionReply;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;

import java.time.Duration;
import java.util.Map;

/**
 * Awaits a previously spawned child agent run.
 *
 * <p>
 * If the child has already completed, returns the result immediately. Otherwise
 * the actor parks the session and this tool requests a lightweight
 * confirmation-pause so the run can be auto-resumed when the child finishes.
 */
public final class AwaitAgentTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "await_agent";

  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Wait for a previously spawned child agent run to complete and return its result. "
          + "Blocks until the child finishes or the timeout is reached.",
      Map.of());

  private static final Duration AWAIT_TIMEOUT = Duration.ofMinutes(30);

  public AwaitAgentTool(final SessionActorFactory actorFactory) {
    super(DESCRIPTOR, actorFactory, true);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) final ToolContext toolContext,
      @ToolSchema(name = "child_session_id", description = "Session ID returned by spawn_agent") final String childSessionId,
      @ToolSchema(name = "child_run_id", description = "Run ID returned by " + SpawnAgentTool.TOOL_NAME + " or " + SendMessageTool.TOOL_NAME) final String childRunId) {
    final ChildRegistry.ChildRunHandle handle = new ChildRegistry.ChildRunHandle(childSessionId, childRunId);
    final SessionReply.AwaitResult result = actorRef(toolContext).<SessionReply.AwaitResult>ask(
        replyTo -> new SessionCommand.ExternalCommand.AwaitChildRun(handle, replyTo), AWAIT_TIMEOUT).toCompletableFuture().join();

    return switch (result) {
      case SessionReply.AwaitResult.Completed(ChildRegistry.ChildRunResult r) -> r.output() != null
          ? Map.of("child_session_id", childSessionId, "result", r.output())
          : Map.of("child_session_id", childSessionId, "status", "completed");
      case SessionReply.AwaitResult.Failed(String reason) -> Map.of("child_session_id", childSessionId, "error", reason);
      case SessionReply.AwaitResult.Parked _ -> {
        ToolUtils.requestConfirmationAndPause(toolContext,
                "Waiting for child agent run to complete.",
                Map.of("child_session_id", childSessionId, "child_run_id", childRunId));
        yield Map.of("child_session_id", childSessionId, "status", "waiting_for_child");
      }
    };
  }
}
