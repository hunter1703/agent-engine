package com.agentengine.runtime.tools.orchestration;

import com.agentengine.runtime.actor.ActorUtils;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionCommand;
import com.agentengine.runtime.actor.SessionReply;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;

import java.util.Map;

/**
 * Sends a new task to an already-spawned child agent session, preserving its
 * conversation history.
 *
 * <p>
 * Unlike {@link SpawnAgentTool}, this does not create a new session — the child
 * retains full context from all prior interactions. The child must have
 * completed its previous task (i.e. be IDLE) before a new one can be assigned.
 * Use {@link AwaitAgentTool} to confirm completion before calling this.
 *
 * <p>
 * This tool is injected per-run by the framework; it is not a CDI singleton.
 */
public final class SendMessageTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "send_task";

  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Send a new message to an already-spawned child agent, preserving its conversation context. "
          + "The child must have completed its previous task. Returns child_session_id and child_run_id. " + "Use "
          + AwaitAgentTool.TOOL_NAME + " to collect the result.",
      Map.of());

  public SendMessageTool(final SessionActorFactory actorFactory) {
    super(DESCRIPTOR, actorFactory);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) final ToolContext toolContext,
      @ToolSchema(name = "child_session_id", description = "Session ID returned by spawn_agent") final String childSessionId,
      @ToolSchema(name = "message", description = "The next task to assign to the child agent") final String message) {
    final SessionReply.SendTaskResult result = actorRef(toolContext).<SessionReply.SendTaskResult>ask(
        replyTo -> new SessionCommand.ExternalCommand.SendChildTask(childSessionId, message, replyTo), ActorUtils.DEFAULT_ASK_TIMEOUT)
        .toCompletableFuture().join();

    return switch (result) {
      case SessionReply.SendTaskResult.TaskAccepted(var handle) ->
        Map.of("child_session_id", handle.childSessionId(), "child_run_id", handle.childRunId());
      case SessionReply.SendTaskResult.Rejected(var reason) -> Map.of("error", "Failed to send task: " + reason);
    };
  }
}
