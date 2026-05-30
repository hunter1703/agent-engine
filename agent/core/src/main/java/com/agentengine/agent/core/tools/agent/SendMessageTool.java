package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.SelfCommand.SendMessageCommand;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.tools.ToolContext;
import java.util.Map;

/**
 * Sends a new message to an already-spawned child agent session, preserving its conversation
 * history.
 *
 * <p>Unlike {@link SpawnAgentTool}, this does not create a new session — the child retains full
 * context from all prior interactions. The child must have completed its previous message (i.e. be
 * IDLE) before a new one can be sent. Use {@link AwaitAgentTool} to confirm completion before
 * calling this.
 *
 * <p>This tool is injected per-run by the framework; it is not a CDI singleton.
 */
public final class SendMessageTool extends AbstractAgentTool {

    public static final String TOOL_NAME = "send_message";

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Sends a follow-up message to an existing child agent session, preserving its full conversation history. "
                    + "Use when the child has completed its previous task but its accumulated context is still "
                    + "relevant — for example, to give corrections, additional instructions, or a new related "
                    + "request without starting a fresh session. The child session must have already finished "
                    + "processing its previous message before a new one is accepted; sending to an active session "
                    + "will be rejected. A successful response confirms the message was accepted and the child "
                    + "has begun processing — it does not mean the child has finished. "
                    + "Returns: { child_session_id } on success, or { error } on failure.",
            Map.of());

    public SendMessageTool(final ActorSystemProvider actorSystemProvider) {
        super(DESCRIPTOR, actorSystemProvider);
    }

    public ToolOutput<Map<String, Object>> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    final ToolContext toolContext,
            @ToolSchema(
                            name = "child_session_id",
                            description =
                                    "The opaque identifier of an existing child agent session to deliver the message to.")
                    final String childSessionId,
            @ToolSchema(
                            name = "message",
                            description =
                                    "The message content to deliver as the next conversation turn to the child session.")
                    final String message) {
        final StartSessionResult result = actorRef(toolContext)
                .<StartSessionResult>ask(
                        replyTo -> new SendMessageCommand(childSessionId, new UniqueRecord<>(message), replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .toCompletableFuture()
                .join();
        return switch (result) {
            case StartSessionResult.Accepted ignored -> ToolOutput.direct(Map.of("child_session_id", childSessionId));
            case StartSessionResult.Rejected(String reason) ->
                ToolOutput.direct(Map.of("error", "Failed to send message: " + reason));
            case StartSessionResult.Queued(int position) ->
                ToolOutput.direct(Map.of(
                        "error", "Failed to send message: unexpected queued response", "queue_position", position));
            default -> ToolOutput.direct(Map.of("error", "Failed to send message: unknown response"));
        };
    }
}
