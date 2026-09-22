package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.ResumeResult;
import com.agentengine.agent.core.session.StartChildResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.context.Context;
import com.google.adk.events.Event;
import java.util.Set;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Commands that the session actor sends to itself.
 *
 * <p>These fall into three groups:
 *
 * <ul>
 *   <li><b>Runner callbacks</b> — events and terminal signals from the async LLM runner.
 *   <li><b>Tool call results</b> — async operations initiated by tool execution, piped back via
 *       {@code pipeToSelf} so they are processed on the actor thread.
 *   <li><b>Loop signals</b> — internal triggers that advance the session's own state machine
 *       (continue the run once all interrupts are resumed, start next queued message).
 * </ul>
 */
public interface SelfCommand extends SessionCommand {

  record PublishEventCommand(Context context, Event event) implements SelfCommand {
    public PublishEventCommand(final Event event) {
      this(Context.current().orElse(null), event);
    }
  }

  record CompleteRunCommand(Context context, String error) implements SelfCommand {
    public CompleteRunCommand() {
      this(Context.current().orElse(null), null);
    }

    public CompleteRunCommand(final String error) {
      this(Context.current().orElse(null), error);
    }
  }

  record StartChildCommand(
      Context context,
      String agentId,
      UniqueRecord<UserMessage> message,
      ActorRef<StartChildResult> replyTo)
      implements SelfCommand {
    public StartChildCommand(
        final String agentId,
        final UniqueRecord<UserMessage> message,
        final ActorRef<StartChildResult> replyTo) {
      this(Context.current().orElse(null), agentId, message, replyTo);
    }
  }

  record AwaitChildCommand(Context context, String childSessionId, ActorRef<RunResult> replyTo)
      implements SelfCommand {
    public AwaitChildCommand(final String childSessionId, final ActorRef<RunResult> replyTo) {
      this(Context.current().orElse(null), childSessionId, replyTo);
    }
  }

  record ResumeChildCommand(
      Context context,
      ResumeRequest resumeRequest,
      ActorRef<ResumeResult> replyTo,
      ResumeResult result,
      String error)
      implements SelfCommand {
    public ResumeChildCommand(
        final ResumeRequest resumeRequest,
        final ActorRef<ResumeResult> replyTo,
        final ResumeResult result,
        final String error) {
      this(Context.current().orElse(null), resumeRequest, replyTo, result, error);
    }
  }

  record StartChildCompletedCommand(
      Context context,
      String sessionId,
      String agentId,
      ActorRef<StartChildResult> replyTo,
      StartSessionResult result,
      String error)
      implements SelfCommand {
    public StartChildCompletedCommand(
        final String sessionId,
        final String agentId,
        final ActorRef<StartChildResult> replyTo,
        final StartSessionResult result,
        final String error) {
      this(Context.current().orElse(null), sessionId, agentId, replyTo, result, error);
    }
  }

  record ContinueRunCommand(Context context) implements SelfCommand {
    public ContinueRunCommand() {
      this(Context.current().orElse(null));
    }
  }

  record SelfPauseCommand(Context context, SessionTopology topology, String interruptId)
      implements SelfCommand {
    public SelfPauseCommand(final SessionTopology topology, final String interruptId) {
      this(Context.current().orElse(null), topology, interruptId);
    }
  }

  record StartNextQueuedMessageCommand(Context context) implements SelfCommand {
    public StartNextQueuedMessageCommand() {
      this(Context.current().orElse(null));
    }
  }

  record DiscardInterruptsCommand(Context context, Set<String> interruptIds)
      implements SelfCommand {
    public DiscardInterruptsCommand(final Set<String> interruptIds) {
      this(Context.current().orElse(null), interruptIds);
    }
  }

  record ReapChildResultCommand(
      Context context,
      ActorRef<RunResult> replyTo,
      String childSessionId,
      int attempt,
      RunResult result,
      Throwable error)
      implements SelfCommand {
    public ReapChildResultCommand(
        final ActorRef<RunResult> replyTo,
        final String childSessionId,
        final int attempt,
        final RunResult result,
        final Throwable error) {
      this(Context.current().orElse(null), replyTo, childSessionId, attempt, result, error);
    }
  }

  record ReapChildCommand(
      Context context, ActorRef<RunResult> replyTo, String childSessionId, int attempt)
      implements SelfCommand {
    public ReapChildCommand(
        final ActorRef<RunResult> replyTo, final String childSessionId, final int attempt) {
      this(Context.current().orElse(null), replyTo, childSessionId, attempt);
    }
  }

  /** Delivers a follow-up message to this session, preserving its existing context. */
  record SendMessageCommand(
      Context context,
      String sessionId,
      UniqueRecord<UserMessage> message,
      ActorRef<StartSessionResult> replyTo)
      implements ParentCommand {
    public SendMessageCommand(
        final String sessionId,
        final UniqueRecord<UserMessage> message,
        final ActorRef<StartSessionResult> replyTo) {
      this(Context.current().orElse(null), sessionId, message, replyTo);
    }
  }
}
