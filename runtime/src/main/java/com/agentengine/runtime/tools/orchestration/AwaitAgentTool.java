package com.agentengine.runtime.tools.orchestration;

import com.agentengine.runtime.actor.ChildRegistry;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionCommand;
import com.agentengine.runtime.actor.SessionReply;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;

import java.time.Duration;
import java.util.Map;

/**
 * Blocks until a previously spawned child agent run completes and returns its
 * result.
 *
 * <p>
 * Race-safe: terminal child results are cached by the parent actor, so late or
 * retried await calls can still return after the child has already completed or
 * failed.
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
      @ToolSchema(name = "child_run_id", description = "Run ID returned by spawn_agent or send_task") final String childRunId) {
    final var handle = new ChildRegistry.ChildRunHandle(childSessionId, childRunId);
    final SessionReply.AwaitResult result = actorRef(toolContext).<SessionReply.AwaitResult>ask(
        replyTo -> new SessionCommand.ExternalCommand.AwaitChildRun(handle, replyTo), AWAIT_TIMEOUT).toCompletableFuture().join();

    return switch (result) {
      case SessionReply.AwaitResult.Completed(var r) -> r.output() != null
          ? Map.of("child_session_id", childSessionId, "result", r.output())
          : Map.of("child_session_id", childSessionId, "status", "completed");
      case SessionReply.AwaitResult.Failed(var reason) -> Map.of("child_session_id", childSessionId, "error", reason);
    };
  }
}
