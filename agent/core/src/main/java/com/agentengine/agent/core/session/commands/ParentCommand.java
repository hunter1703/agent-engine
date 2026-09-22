package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.util.context.Context;
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
  record InitializeCommand(Context context, SessionTopology topology, ActorRef<Done> replyTo)
      implements ParentCommand {
    public InitializeCommand(final SessionTopology topology, final ActorRef<Done> replyTo) {
      this(Context.current().orElse(null), topology, replyTo);
    }
  }

  /** Asks this session to report its current result. Used by parent to poll child completion. */
  record AwaitCommand(Context context, ActorRef<RunResult> replyTo) implements ParentCommand {
    public AwaitCommand(final ActorRef<RunResult> replyTo) {
      this(Context.current().orElse(null), replyTo);
    }
  }
}
