package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.CurrentTurnEvents;
import com.agentengine.agent.core.session.ResumeResult;
import com.agentengine.agent.core.session.RollbackResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.context.Context;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that any party may send to a session actor — users via the API, peer sessions, or parent
 * sessions. No relationship constraint is required to send these.
 */
public interface ExternalCommand extends SessionCommand {

  record StartCommand(
      Context context, UniqueRecord<UserMessage> message, ActorRef<StartSessionResult> replyTo)
      implements ExternalCommand {
    public StartCommand(
        final UniqueRecord<UserMessage> message, final ActorRef<StartSessionResult> replyTo) {
      this(Context.current().orElse(null), message, replyTo);
    }
  }

  record ResumeCommand(Context context, ResumeRequest resumeRequest, ActorRef<ResumeResult> replyTo)
      implements ExternalCommand {
    public ResumeCommand(final ResumeRequest resumeRequest, final ActorRef<ResumeResult> replyTo) {
      this(Context.current().orElse(null), resumeRequest, replyTo);
    }
  }

  /**
   * The reply contains all events accumulated in {@code SessionActor.turnEvents} since the last
   * turn commit, mapped to {@link com.agentengine.util.agents.beans.SessionEvent} with correct
   * sequence numbers.
   */
  record GetCurrentTurnEventsCommand(Context context, ActorRef<CurrentTurnEvents> replyTo)
      implements ExternalCommand {
    public GetCurrentTurnEventsCommand(final ActorRef<CurrentTurnEvents> replyTo) {
      this(Context.current().orElse(null), replyTo);
    }
  }

  /**
   * Appends a {@link com.agentengine.agent.core.session.events.RollbackFact} to the journal,
   * discarding all events from the given run onwards.
   *
   * <p>The rollback is non-destructive: history is preserved in the journal and the effective event
   * view is computed on read. Only valid when the session is not currently running.
   */
  record RollbackCommand(Context context, String runId, ActorRef<RollbackResult> replyTo)
      implements ExternalCommand {
    public RollbackCommand(final String runId, final ActorRef<RollbackResult> replyTo) {
      this(Context.current().orElse(null), runId, replyTo);
    }
  }
}
