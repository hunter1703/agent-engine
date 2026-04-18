package com.agentengine.runtime.session;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.pekko.PekkoSerializable;
import java.util.List;

/**
 * Reply to {@link com.agentengine.runtime.session.commands.ExternalCommand.GetCurrentTurnEventsCommand}.
 *
 * <p>Wraps the event list in a {@link PekkoSerializable} record so that Pekko Artery can
 * serialize the reply via Jackson-CBOR when the session actor is on a remote cluster node.
 * A plain {@code List<SessionEvent>} reply uses {@code ImmutableCollections$ListN}, which has
 * no registered Pekko serializer and causes the ask to time out in multi-node deployments.
 */
public record CurrentTurnEvents(List<SessionEvent> events) implements PekkoSerializable {}
