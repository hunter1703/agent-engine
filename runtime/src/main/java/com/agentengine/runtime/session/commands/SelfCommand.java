package com.agentengine.runtime.session.commands;

import com.agentengine.runtime.session.ConfirmResult;
import com.agentengine.runtime.session.StartChildResult;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.beans.UniqueRecord;
import com.google.adk.events.Event;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that the session actor sends to itself.
 *
 * <p>These fall into three groups:
 * <ul>
 *   <li><b>Runner callbacks</b> — events and terminal signals from the async LLM runner.</li>
 *   <li><b>Tool call results</b> — async operations initiated by tool execution, piped back via
 *       {@code pipeToSelf} so they are processed on the actor thread.</li>
 *   <li><b>Loop signals</b> — internal triggers that advance the session's own state machine
 *       (resume after confirmation, start next queued message).</li>
 * </ul>
 */
public interface SelfCommand extends SessionCommand {

    record PublishEventCommand(Event event) implements SelfCommand {}

    record RunFailedCommand(String error) implements SelfCommand {}

    record CompleteRunCommand() implements SelfCommand {}

    record StartChildCommand(String agentId, UniqueRecord<String> message, ActorRef<StartChildResult> replyTo)
            implements SelfCommand {}

    record AwaitChildCommand(String childSessionId, ActorRef<RunResult> replyTo) implements SelfCommand {}

    record ConfirmChildCommand(
            Confirmation confirmation, ActorRef<ConfirmResult> replyTo, ConfirmResult result, String error)
            implements SelfCommand {}

    record StartChildCompletedCommand(
            String sessionId,
            String agentId,
            ActorRef<StartChildResult> replyTo,
            StartSessionResult result,
            String error)
            implements SelfCommand {}

    record ResumeCommand() implements SelfCommand {}

    record StartNextQueuedMessageCommand() implements SelfCommand {}

    /** Delivers a follow-up message to this session, preserving its existing context. */
    record SendMessageCommand(String sessionId, UniqueRecord<String> message, ActorRef<StartSessionResult> replyTo)
            implements ParentCommand {}
}
