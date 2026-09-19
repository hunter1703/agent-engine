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
public abstract class ParentCommand extends SessionCommand {

  /** Establishes the session's identity and position in the graph. Sent once on first spawn. */
  public static final class InitializeCommand extends ParentCommand {
    private SessionTopology topology;
    private ActorRef<Done> replyTo;

    public InitializeCommand() {}

    public InitializeCommand(final SessionTopology topology, final ActorRef<Done> replyTo) {
      this.topology = topology;
      this.replyTo = replyTo;
    }

    public SessionTopology getTopology() {
      return topology;
    }

    public void setTopology(final SessionTopology topology) {
      this.topology = topology;
    }

    public ActorRef<Done> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<Done> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /** Asks this session to report its current result. Used by parent to poll child completion. */
  public static final class AwaitCommand extends ParentCommand {
    private ActorRef<RunResult> replyTo;

    public AwaitCommand() {}

    public AwaitCommand(final ActorRef<RunResult> replyTo) {
      this.replyTo = replyTo;
    }

    public ActorRef<RunResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<RunResult> replyTo) {
      this.replyTo = replyTo;
    }
  }
}
