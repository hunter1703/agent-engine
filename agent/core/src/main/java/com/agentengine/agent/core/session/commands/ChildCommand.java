package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.context.Context;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that only a child session may send to its parent session actor.
 *
 * <p>These represent lifecycle signals flowing upward in the graph — a child notifying its parent
 * that it has paused waiting for an interrupt to be resumed, or that its run has finished.
 */
public interface ChildCommand extends SessionCommand {

  /**
   * Notifies the parent that this child has paused and is waiting for an external interrupt to be
   * resumed.
   */
  record PauseChildCommand(
      Context context, String childSessionId, String interruptId, ActorRef<Done> replyTo)
      implements ChildCommand {
    public PauseChildCommand(
        final String childSessionId, final String interruptId, final ActorRef<Done> replyTo) {
      this(Context.current().orElse(null), childSessionId, interruptId, replyTo);
    }
  }

  /** Notifies the parent that this child's run has finished with a result. */
  record CompleteChildCommand(Context context, String childSessionId, RunResult result)
      implements ChildCommand {
    public CompleteChildCommand(final String childSessionId, final RunResult result) {
      this(Context.current().orElse(null), childSessionId, result);
    }
  }
}
