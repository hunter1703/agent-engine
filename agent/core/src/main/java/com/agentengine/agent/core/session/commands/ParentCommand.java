package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.core.session.state.SessionTopology;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that only a parent session may send to a child session actor.
 *
 * <p>These commands assume a direct parent-child relationship. A peer or unrelated session has no
 * authority to initialize, inject messages into, or query the result of another session's children.
 */
public interface ParentCommand extends SessionCommand {

  /** Establishes the session's identity and position in the graph. Sent once on first spawn. */
  record InitializeCommand(SessionTopology topology, ActorRef<Done> replyTo)
      implements ParentCommand {}

  /** Asks this session to report its current result. Used by parent to poll child completion. */
  record AwaitCommand(ActorRef<RunResult> replyTo) implements ParentCommand {}
}
