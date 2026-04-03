package com.agentengine.runtime.session.commands;

import com.agentengine.runtime.session.ConfirmResult;
import com.agentengine.runtime.session.StartChildResult;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.util.agents.beans.Confirmation;
import com.google.adk.events.Event;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;

public interface InternalCommand extends SessionCommand {

    record PublishEventCommand(Event event) implements InternalCommand {}

    record ChildPausedCommand(String childSessionId, String confirmationId, ActorRef<Done> replyTo)
            implements InternalCommand {}

    record RunFailedCommand(String error) implements InternalCommand {}

    record ConfirmChildCommand(
            Confirmation confirmation, ActorRef<ConfirmResult> replyTo, ConfirmResult result, String error)
            implements InternalCommand {}

    record StartChildCompletedCommand(
            String sessionId,
            String agentId,
            ActorRef<StartChildResult> replyTo,
            StartSessionResult result,
            String error)
            implements InternalCommand {}

    record StartNextQueuedMessageCommand() implements InternalCommand {}

    record ResumeCommand() implements InternalCommand {}
}
