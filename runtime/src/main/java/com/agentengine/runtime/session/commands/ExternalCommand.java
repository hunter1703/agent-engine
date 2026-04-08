package com.agentengine.runtime.session.commands;

import com.agentengine.runtime.session.ConfirmResult;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.beans.UniqueRecord;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that any party may send to a session actor — users via the API, peer sessions,
 * or parent sessions. No relationship constraint is required to send these.
 */
public interface ExternalCommand extends SessionCommand {

    record StartCommand(UniqueRecord<String> message, ActorRef<StartSessionResult> replyTo)
            implements ExternalCommand {}

    record ConfirmCommand(Confirmation confirmation, ActorRef<ConfirmResult> replyTo) implements ExternalCommand {}
}
