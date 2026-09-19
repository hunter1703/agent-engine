package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.CurrentTurnEvents;
import com.agentengine.agent.core.session.ResumeResult;
import com.agentengine.agent.core.session.RollbackResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.beans.UniqueRecord;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that any party may send to a session actor — users via the API, peer sessions, or parent
 * sessions. No relationship constraint is required to send these.
 */
public abstract class ExternalCommand extends SessionCommand {

  public static final class StartCommand extends ExternalCommand {
    private UniqueRecord<UserMessage> message;
    private ActorRef<StartSessionResult> replyTo;

    public StartCommand() {}

    public StartCommand(
        final UniqueRecord<UserMessage> message, final ActorRef<StartSessionResult> replyTo) {
      this.message = message;
      this.replyTo = replyTo;
    }

    public UniqueRecord<UserMessage> getMessage() {
      return message;
    }

    public void setMessage(final UniqueRecord<UserMessage> message) {
      this.message = message;
    }

    public ActorRef<StartSessionResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<StartSessionResult> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public static final class ResumeCommand extends ExternalCommand {
    private ResumeRequest resumeRequest;
    private ActorRef<ResumeResult> replyTo;

    public ResumeCommand() {}

    public ResumeCommand(final ResumeRequest resumeRequest, final ActorRef<ResumeResult> replyTo) {
      this.resumeRequest = resumeRequest;
      this.replyTo = replyTo;
    }

    public ResumeRequest getResumeRequest() {
      return resumeRequest;
    }

    public void setResumeRequest(final ResumeRequest resumeRequest) {
      this.resumeRequest = resumeRequest;
    }

    public ActorRef<ResumeResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<ResumeResult> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * The reply contains all events accumulated in {@code SessionActor.turnEvents} since the last
   * turn commit, mapped to {@link com.agentengine.util.agents.beans.SessionEvent} with correct
   * sequence numbers.
   */
  public static final class GetCurrentTurnEventsCommand extends ExternalCommand {
    private ActorRef<CurrentTurnEvents> replyTo;

    public GetCurrentTurnEventsCommand() {}

    public GetCurrentTurnEventsCommand(final ActorRef<CurrentTurnEvents> replyTo) {
      this.replyTo = replyTo;
    }

    public ActorRef<CurrentTurnEvents> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<CurrentTurnEvents> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * Appends a {@link com.agentengine.agent.core.session.events.RollbackFact} to the journal,
   * discarding all events from the given run onwards.
   *
   * <p>The rollback is non-destructive: history is preserved in the journal and the effective event
   * view is computed on read. Only valid when the session is not currently running.
   */
  public static final class RollbackCommand extends ExternalCommand {
    private String runId;
    private ActorRef<RollbackResult> replyTo;

    public RollbackCommand() {}

    public RollbackCommand(final String runId, final ActorRef<RollbackResult> replyTo) {
      this.runId = runId;
      this.replyTo = replyTo;
    }

    public String getRunId() {
      return runId;
    }

    public void setRunId(final String runId) {
      this.runId = runId;
    }

    public ActorRef<RollbackResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<RollbackResult> replyTo) {
      this.replyTo = replyTo;
    }
  }
}
