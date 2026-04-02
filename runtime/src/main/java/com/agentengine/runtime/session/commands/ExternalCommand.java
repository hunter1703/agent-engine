package com.agentengine.runtime.session.commands;

import com.agentengine.runtime.session.ConfirmResult;
import com.agentengine.runtime.session.SessionActor;
import com.agentengine.runtime.session.StartChildResult;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.runtime.session.state.SessionTopology;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.beans.UniqueRecord;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;

/** Public commands accepted by {@link SessionActor}. */
public interface ExternalCommand extends SessionCommand {

    record InitializeCommand(SessionTopology topology, ActorRef<Done> replyTo) implements ExternalCommand {}

    record StartCommand(UniqueRecord<String> message, ActorRef<StartSessionResult> replyTo)
            implements ExternalCommand {}

    record StartChildCommand(String agentId, UniqueRecord<String> message, ActorRef<StartChildResult> replyTo)
            implements ExternalCommand {}

    record SendMessageCommand(String sessionId, UniqueRecord<String> message, ActorRef<StartSessionResult> replyTo)
            implements ExternalCommand {}

    record ConfirmCommand(Confirmation confirmation, ActorRef<ConfirmResult> replyTo) implements ExternalCommand {}

    record AwaitCommand(String childSessionId, ActorRef<RunResult> replyTo) implements ExternalCommand {}
}
