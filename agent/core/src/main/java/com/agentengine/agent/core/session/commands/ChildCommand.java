package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.core.session.events.RunResult;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that only a child session may send to its parent session actor.
 *
 * <p>These represent lifecycle signals flowing upward in the graph — a child notifying its parent
 * that it has paused waiting for an interrupt to be resumed, or that its run has finished.
 */
public abstract class ChildCommand extends SessionCommand {

  /**
   * Notifies the parent that this child has paused and is waiting for an external interrupt to be
   * resumed.
   */
  public static final class PauseChildCommand extends ChildCommand {
    private String childSessionId;
    private String interruptId;
    private ActorRef<Done> replyTo;

    public PauseChildCommand() {}

    public PauseChildCommand(
        final String childSessionId, final String interruptId, final ActorRef<Done> replyTo) {
      this.childSessionId = childSessionId;
      this.interruptId = interruptId;
      this.replyTo = replyTo;
    }

    public String getChildSessionId() {
      return childSessionId;
    }

    public void setChildSessionId(final String childSessionId) {
      this.childSessionId = childSessionId;
    }

    public String getInterruptId() {
      return interruptId;
    }

    public void setInterruptId(final String interruptId) {
      this.interruptId = interruptId;
    }

    public ActorRef<Done> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<Done> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /** Notifies the parent that this child's run has finished with a result. */
  public static final class CompleteChildCommand extends ChildCommand {
    private String childSessionId;
    private RunResult result;

    public CompleteChildCommand() {}

    public CompleteChildCommand(final String childSessionId, final RunResult result) {
      this.childSessionId = childSessionId;
      this.result = result;
    }

    public String getChildSessionId() {
      return childSessionId;
    }

    public void setChildSessionId(final String childSessionId) {
      this.childSessionId = childSessionId;
    }

    public RunResult getResult() {
      return result;
    }

    public void setResult(final RunResult result) {
      this.result = result;
    }
  }
}
