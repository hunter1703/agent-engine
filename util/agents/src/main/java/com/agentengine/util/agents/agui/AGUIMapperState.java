package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.StringUtils;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable state for {@link AGUIEventMapper}.
 *
 * <p>One mapper instance handles the entire SSE stream for a single HTTP invocation, but that
 * stream can carry events from more than one {@code sessionId}. Run/step/message/tool-call tracking
 * is therefore scoped per source {@code sessionId} via {@link RunScope}, so one session's in-flight
 * run can never be started, finished, or have its steps/messages clobbered by an interleaved event
 * from another session.
 */
public final class AGUIMapperState {

  private final String sessionId;
  private final String agentId;
  private final Map<String, RunScope> sessionVsScope = new HashMap<>();
  private final Map<String, FunctionCall> idVsFunctionCall = new HashMap<>();

  private String currentEventSessionId;
  private long currentSourceTimestamp;
  private String currentSourceEventId;
  private String currentAuthor;
  private String currentRole;

  public AGUIMapperState(final String sessionId, final String agentId) {
    this.sessionId = sessionId;
    this.agentId = agentId;
  }

  /**
   * Must be called first for every incoming event so subsequent state lookups are scoped correctly.
   */
  public void recordSourceEvent(final SessionEvent event) {
    currentEventSessionId = event.getSessionId();
    currentSourceTimestamp = event.getTimestamp();
    currentSourceEventId = event.getId();
    currentAuthor = event.getAuthor();
    final Content content = event.getContent();
    currentRole = content != null ? content.role().orElse(null) : null;
    if (content != null) {
      content
          .parts()
          .orElse(List.of())
          .forEach(
              part ->
                  part.functionCall()
                      .filter(functionCall -> functionCall.id().isPresent())
                      .ifPresent(
                          functionCall ->
                              idVsFunctionCall.put(functionCall.id().get(), functionCall)));
    }
  }

  public boolean hasNewRun(final String candidateRunId) {
    return candidateRunId != null && !Objects.equals(runScope().runId, candidateRunId);
  }

  public void startRun(final String runId) {
    runScope().runId = runId;
  }

  /**
   * Clears the tracked run for the current event's session and returns the runId that was active.
   */
  public String finishRun() {
    final RunScope scope = runScope();
    final String finishedRunId = scope.runId;
    scope.runId = null;
    return finishedRunId;
  }

  public String currentRunId() {
    return runScope().runId;
  }

  public boolean hasStartedStep() {
    return StringUtils.isNotBlank(runScope().currentStepName);
  }

  public String startNextStep() {
    return runScope().startNextStep(currentSourceEventId);
  }

  public String finishStep() {
    return runScope().finishStep();
  }

  public boolean hasOpenTextMessage() {
    return runScope().currentTextMessageId != null;
  }

  public String startNextTextMessage() {
    return runScope().startNextTextMessage(currentSourceEventId);
  }

  public String currentTextMessageId() {
    return runScope().currentTextMessageId;
  }

  public String currentReasoningMessageId() {
    return runScope().currentReasoningMessageId;
  }

  public void resetTextMessage() {
    runScope().currentTextMessageId = null;
  }

  public boolean hasOpenReasoning() {
    return runScope().reasoningOpen;
  }

  public String startReasoning() {
    return runScope().startReasoning(currentSourceEventId);
  }

  public String currentReasoningId() {
    return runScope().currentReasoningId;
  }

  public boolean hasOpenReasoningMessage() {
    return runScope().reasoningMessageOpen;
  }

  public String startReasoningMessage() {
    return runScope().startReasoningMessage(currentSourceEventId);
  }

  public void closeReasoningMessage() {
    final RunScope scope = runScope();
    scope.reasoningMessageOpen = false;
    scope.currentReasoningMessageId = null;
  }

  /**
   * Distinguishes, for the currently open text or reasoning message, a genuinely new non-partial
   * arrival (false: nothing streamed yet, so this is the message's first and only content) from the
   * model's redundant full-response echo (true: partial deltas already streamed this message's
   * content, so a later non-partial event repeats it rather than adding to it).
   *
   * <p>Text and reasoning messages are mutually exclusive within a scope — {@code mapText} closes
   * any open reasoning message before opening a text one and vice versa — so one flag, reset
   * whenever either kind of message opens, covers both without ambiguity.
   */
  public boolean hasStreamedPartialContent() {
    return runScope().partialContentStreamed;
  }

  public void markPartialContentStreamed() {
    runScope().partialContentStreamed = true;
  }

  public void closeReasoning() {
    final RunScope scope = runScope();
    scope.reasoningOpen = false;
    scope.currentReasoningId = null;
  }

  /**
   * Mints a fresh, stable id for a tool call's result message — every {@code ToolCallResultEvent}
   * introduces a new conversation message (mirroring {@code ToolMessage} in the AG-UI message
   * model), so this is never the id of the call's parent step or of any other message.
   */
  public String nextToolResultMessageId() {
    return runScope().nextToolResultMessageId(currentSourceEventId);
  }

  /** Returns the timestamp to stamp onto the current event, falling back to wall clock. */
  public long timestamp() {
    return currentSourceTimestamp > 0 ? currentSourceTimestamp : System.currentTimeMillis();
  }

  public String sessionId() {
    return sessionId;
  }

  public String agentId() {
    return agentId;
  }

  public String currentAuthor() {
    return currentAuthor != null ? currentAuthor : agentId;
  }

  public String currentRole() {
    return currentRole;
  }

  public FunctionCall getFunctionCall(final String callId) {
    return idVsFunctionCall.get(callId);
  }

  private RunScope runScope() {
    return sessionVsScope.computeIfAbsent(currentEventSessionId, ignored -> new RunScope());
  }

  /** Run/step/message tracking for a single source session */
  private static final class RunScope {
    private String runId;
    private String currentStepName;
    private int stepSequence;

    private String currentTextMessageId;
    private int textMessageSequence;
    private int toolResultMessageSequence;

    private String currentReasoningId;
    private int reasoningSequence;
    private String currentReasoningMessageId;
    private boolean reasoningOpen;
    private boolean reasoningMessageOpen;
    private int reasoningMessageSequence;

    private boolean partialContentStreamed;

    private String startNextStep(final String sourceEventId) {
      final String prefix = runId != null ? "step-" + shortId(runId) + "-" : "step-";
      currentStepName = stableReplayId(prefix, sourceEventId, ++stepSequence);
      return currentStepName;
    }

    private String finishStep() {
      final String stepName = currentStepName;
      currentStepName = null;
      return stepName;
    }

    private String startReasoning(final String sourceEventId) {
      final String prefix = runId != null ? "reasoning-" + shortId(runId) + "-" : "reasoning-";
      reasoningOpen = true;
      currentReasoningId = stableReplayId(prefix, sourceEventId, ++reasoningSequence);
      return currentReasoningId;
    }

    private String startNextTextMessage(final String sourceEventId) {
      final String prefix = runId != null ? "msg-" + shortId(runId) + "-" : "msg-";
      currentTextMessageId = stableReplayId(prefix, sourceEventId, ++textMessageSequence);
      partialContentStreamed = false;
      return currentTextMessageId;
    }

    private String nextToolResultMessageId(final String sourceEventId) {
      final String prefix = runId != null ? "toolresult-" + shortId(runId) + "-" : "toolresult-";
      return stableReplayId(prefix, sourceEventId, ++toolResultMessageSequence);
    }

    private String startReasoningMessage(final String sourceEventId) {
      final String prefix = runId != null ? "think-" + shortId(runId) + "-" : "think-";
      reasoningMessageOpen = true;
      currentReasoningMessageId = stableReplayId(prefix, sourceEventId, ++reasoningMessageSequence);
      partialContentStreamed = false;
      return currentReasoningMessageId;
    }
  }

  private static String shortId(final String id) {
    if (id == null || id.length() <= 8) {
      return id != null ? id : "";
    }
    return id.substring(id.length() - 8);
  }

  private static String stableReplayId(
      final String prefix, final String sourceEventId, final int sequence) {
    if (StringUtils.isNotBlank(sourceEventId)) {
      return prefix + shortId(sourceEventId) + "-" + sequence;
    }
    return prefix + sequence;
  }
}
