package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.ConfirmResult;
import com.agentengine.agent.core.session.CurrentTurnEvents;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.beans.UniqueRecord;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that any party may send to a session actor — users via the API, peer sessions,
 * or parent sessions. No relationship constraint is required to send these.
 */
public interface ExternalCommand extends SessionCommand {

    record StartCommand(UniqueRecord<UserMessage> message, ActorRef<StartSessionResult> replyTo)
            implements ExternalCommand {}

    record ConfirmCommand(Confirmation confirmation, ActorRef<ConfirmResult> replyTo) implements ExternalCommand {}

    /**
     * Requests the current uncommitted turn events from the session actor.
     *
     * <p>The reply contains all events accumulated in {@code SessionActor.turnEvents} since the
     * last turn commit, mapped to {@link com.agentengine.util.agents.beans.SessionEvent} with correct sequence numbers.
     */
    record GetCurrentTurnEventsCommand(ActorRef<CurrentTurnEvents> replyTo) implements ExternalCommand {}
}
