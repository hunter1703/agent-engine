package com.agentengine.runtime.tools.agent;

import com.agentengine.runtime.actor.ChildRegistry;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionCommand;
import com.agentengine.runtime.actor.SessionReply;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;

import java.util.Map;

/**
 * Spawns a new child agent session and starts it with the given message.
 *
 * <p>
 * The child runs asynchronously. Use {@link AwaitAgentTool} to collect its
 * result, or {@link SendMessageTool} to send follow-up messages while preserving
 * its context. Both tools require the child_session_id and child_run_id
 * returned by this tool. This tool is injected per-run; it is not a CDI
 * singleton.
 */
public final class SpawnAgentTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "spawn_agent";

  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Spawn a child agent to handle a subtask. Returns child_session_id and child_run_id "
          + "immediately without waiting for the child to complete. Use await_agent to collect the result.",
      Map.of());

  public SpawnAgentTool(final SessionActorFactory actorFactory) {
    super(DESCRIPTOR, actorFactory);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) final ToolContext toolContext,
      @ToolSchema(name = "agent_id", description = "ID of the agent to spawn") final String childAgentId,
      @ToolSchema(name = "message", description = "Initial message to send to the spawned agent") final String message) {
    final SessionReply.SpawnResult result = actorRef(toolContext).<SessionReply.SpawnResult>ask(
        replyTo -> new SessionCommand.ExternalCommand.SpawnChild(childAgentId, message, replyTo), SessionActorFactory.ASK_TIMEOUT)
        .toCompletableFuture().join();

    return switch (result) {
      case SessionReply.SpawnResult.ChildSpawned(ChildRegistry.ChildRunHandle handle) ->
        Map.of("child_session_id", handle.childSessionId(), "child_run_id", handle.childRunId());
      case SessionReply.SpawnResult.Rejected(String reason) -> Map.of("error", "Failed to spawn agent: " + reason);
    };
  }
}
