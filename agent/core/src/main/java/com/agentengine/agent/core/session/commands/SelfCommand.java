package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.ResumeResult;
import com.agentengine.agent.core.session.StartChildResult;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.beans.UniqueRecord;
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
public abstract class SelfCommand extends SessionCommand {

  public static final class PublishEventCommand extends SelfCommand {
    private Event event;

    public PublishEventCommand() {}

    public PublishEventCommand(final Event event) {
      this.event = event;
    }

    public Event getEvent() {
      return event;
    }

    public void setEvent(final Event event) {
      this.event = event;
    }
  }

  public static final class CompleteRunCommand extends SelfCommand {
    private String error;

    public CompleteRunCommand() {}

    public CompleteRunCommand(final String error) {
      this.error = error;
    }

    public String getError() {
      return error;
    }

    public void setError(final String error) {
      this.error = error;
    }
  }

  public static final class StartChildCommand extends SelfCommand {
    private String agentId;
    private UniqueRecord<UserMessage> message;
    private ActorRef<StartChildResult> replyTo;

    public StartChildCommand() {}

    public StartChildCommand(
        final String agentId,
        final UniqueRecord<UserMessage> message,
        final ActorRef<StartChildResult> replyTo) {
      this.agentId = agentId;
      this.message = message;
      this.replyTo = replyTo;
    }

    public String getAgentId() {
      return agentId;
    }

    public void setAgentId(final String agentId) {
      this.agentId = agentId;
    }

    public UniqueRecord<UserMessage> getMessage() {
      return message;
    }

    public void setMessage(final UniqueRecord<UserMessage> message) {
      this.message = message;
    }

    public ActorRef<StartChildResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<StartChildResult> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public static final class AwaitChildCommand extends SelfCommand {
    private String childSessionId;
    private ActorRef<RunResult> replyTo;

    public AwaitChildCommand() {}

    public AwaitChildCommand(final String childSessionId, final ActorRef<RunResult> replyTo) {
      this.childSessionId = childSessionId;
      this.replyTo = replyTo;
    }

    public String getChildSessionId() {
      return childSessionId;
    }

    public void setChildSessionId(final String childSessionId) {
      this.childSessionId = childSessionId;
    }

    public ActorRef<RunResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<RunResult> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public static final class ResumeChildCommand extends SelfCommand {
    private ResumeRequest resumeRequest;
    private ActorRef<ResumeResult> replyTo;
    private ResumeResult result;
    private String error;

    public ResumeChildCommand() {}

    public ResumeChildCommand(
        final ResumeRequest resumeRequest,
        final ActorRef<ResumeResult> replyTo,
        final ResumeResult result,
        final String error) {
      this.resumeRequest = resumeRequest;
      this.replyTo = replyTo;
      this.result = result;
      this.error = error;
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

    public ResumeResult getResult() {
      return result;
    }

    public void setResult(final ResumeResult result) {
      this.result = result;
    }

    public String getError() {
      return error;
    }

    public void setError(final String error) {
      this.error = error;
    }
  }

  public static final class StartChildCompletedCommand extends SelfCommand {
    private String sessionId;
    private String agentId;
    private ActorRef<StartChildResult> replyTo;
    private StartSessionResult result;
    private String error;

    public StartChildCompletedCommand() {}

    public StartChildCompletedCommand(
        final String sessionId,
        final String agentId,
        final ActorRef<StartChildResult> replyTo,
        final StartSessionResult result,
        final String error) {
      this.sessionId = sessionId;
      this.agentId = agentId;
      this.replyTo = replyTo;
      this.result = result;
      this.error = error;
    }

    public String getSessionId() {
      return sessionId;
    }

    public void setSessionId(final String sessionId) {
      this.sessionId = sessionId;
    }

    public String getAgentId() {
      return agentId;
    }

    public void setAgentId(final String agentId) {
      this.agentId = agentId;
    }

    public ActorRef<StartChildResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<StartChildResult> replyTo) {
      this.replyTo = replyTo;
    }

    public StartSessionResult getResult() {
      return result;
    }

    public void setResult(final StartSessionResult result) {
      this.result = result;
    }

    public String getError() {
      return error;
    }

    public void setError(final String error) {
      this.error = error;
    }
  }

  public static final class ContinueRunCommand extends SelfCommand {

    public ContinueRunCommand() {}
  }

  public static final class SelfPauseCommand extends SelfCommand {
    private SessionTopology topology;
    private String interruptId;

    public SelfPauseCommand() {}

    public SelfPauseCommand(final SessionTopology topology, final String interruptId) {
      this.topology = topology;
      this.interruptId = interruptId;
    }

    public SessionTopology getTopology() {
      return topology;
    }

    public void setTopology(final SessionTopology topology) {
      this.topology = topology;
    }

    public String getInterruptId() {
      return interruptId;
    }

    public void setInterruptId(final String interruptId) {
      this.interruptId = interruptId;
    }
  }

  public static final class StartNextQueuedMessageCommand extends SelfCommand {

    public StartNextQueuedMessageCommand() {}
  }

  public static final class DiscardInterruptsCommand extends SelfCommand {
    private Set<String> interruptIds;

    public DiscardInterruptsCommand() {}

    public DiscardInterruptsCommand(final Set<String> interruptIds) {
      this.interruptIds = interruptIds;
    }

    public Set<String> getInterruptIds() {
      return interruptIds;
    }

    public void setInterruptIds(final Set<String> interruptIds) {
      this.interruptIds = interruptIds;
    }
  }

  public static final class ReapChildResultCommand extends SelfCommand {
    private ActorRef<RunResult> replyTo;
    private String childSessionId;
    private int attempt;
    private RunResult result;
    private Throwable error;

    public ReapChildResultCommand() {}

    public ReapChildResultCommand(
        final ActorRef<RunResult> replyTo,
        final String childSessionId,
        final int attempt,
        final RunResult result,
        final Throwable error) {
      this.replyTo = replyTo;
      this.childSessionId = childSessionId;
      this.attempt = attempt;
      this.result = result;
      this.error = error;
    }

    public ActorRef<RunResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<RunResult> replyTo) {
      this.replyTo = replyTo;
    }

    public String getChildSessionId() {
      return childSessionId;
    }

    public void setChildSessionId(final String childSessionId) {
      this.childSessionId = childSessionId;
    }

    public int getAttempt() {
      return attempt;
    }

    public void setAttempt(final int attempt) {
      this.attempt = attempt;
    }

    public RunResult getResult() {
      return result;
    }

    public void setResult(final RunResult result) {
      this.result = result;
    }

    public Throwable getError() {
      return error;
    }

    public void setError(final Throwable error) {
      this.error = error;
    }
  }

  public static final class ReapChildCommand extends SelfCommand {
    private ActorRef<RunResult> replyTo;
    private String childSessionId;
    private int attempt;

    public ReapChildCommand() {}

    public ReapChildCommand(
        final ActorRef<RunResult> replyTo, final String childSessionId, final int attempt) {
      this.replyTo = replyTo;
      this.childSessionId = childSessionId;
      this.attempt = attempt;
    }

    public ActorRef<RunResult> getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(final ActorRef<RunResult> replyTo) {
      this.replyTo = replyTo;
    }

    public String getChildSessionId() {
      return childSessionId;
    }

    public void setChildSessionId(final String childSessionId) {
      this.childSessionId = childSessionId;
    }

    public int getAttempt() {
      return attempt;
    }

    public void setAttempt(final int attempt) {
      this.attempt = attempt;
    }
  }

  /** Delivers a follow-up message to this session, preserving its existing context. */
  public static final class SendMessageCommand extends ParentCommand {
    private String sessionId;
    private UniqueRecord<UserMessage> message;
    private ActorRef<StartSessionResult> replyTo;

    public SendMessageCommand() {}

    public SendMessageCommand(
        final String sessionId,
        final UniqueRecord<UserMessage> message,
        final ActorRef<StartSessionResult> replyTo) {
      this.sessionId = sessionId;
      this.message = message;
      this.replyTo = replyTo;
    }

    public String getSessionId() {
      return sessionId;
    }

    public void setSessionId(final String sessionId) {
      this.sessionId = sessionId;
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
}
