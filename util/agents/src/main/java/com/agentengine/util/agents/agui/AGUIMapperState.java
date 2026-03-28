package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AGUIMapperState {

  private final String sessionId;
  private final String agentId;
  private final StringBuilder textBuffer = new StringBuilder();
  private final Map<String, String> toolCallParentSteps = new HashMap<>();

  private String runId;
  private String currentStepName;
  private String currentTextMessageId;
  private String currentReasoningId;
  private String currentReasoningMessageId;
  private String finalAnswer;
  private long currentSourceTimestamp;
  private String currentSourceEventId;
  private int stepSequence;
  private int textMessageSequence;
  private int reasoningSequence;
  private int reasoningMessageSequence;

  public AGUIMapperState(final String sessionId, final String agentId) {
    this.sessionId = sessionId;
    this.agentId = agentId;
  }

  public void recordSourceEvent(final SessionEvent event) {
    currentSourceTimestamp = event.getTimestamp();
    currentSourceEventId = event.getId();
  }

  public boolean hasNewRun(final String candidateRunId) {
    return candidateRunId != null && !Objects.equals(runId, candidateRunId);
  }

  public void startRun(final String runId) {
    this.runId = runId;
  }

  public String finishRun() {
    final String finishedRunId = runId;
    runId = null;
    return finishedRunId;
  }

  public String currentRunId() {
    return runId;
  }

  public boolean hasStartedStep() {
    return StringUtils.isNotBlank(currentStepName);
  }

  public String startNextStep() {
    currentStepName = stableReplayId("step-", currentSourceEventId, ++stepSequence);
    return currentStepName;
  }

  public String currentStepName() {
    return currentStepName;
  }

  public String finishStep() {
    final String stepName = currentStepName;
    currentStepName = null;
    toolCallParentSteps.clear();
    return stepName;
  }

  public boolean hasOpenTextMessage() {
    return currentTextMessageId != null;
  }

  public String startNextTextMessage() {
    currentTextMessageId = nextReplayTextMessageId(currentSourceEventId);
    return currentTextMessageId;
  }

  public String nextReplayTextMessageId(final String sourceEventId) {
    return stableReplayId("msg-", sourceEventId, ++textMessageSequence);
  }

  public String currentTextMessageId() {
    return currentTextMessageId;
  }

  public boolean isTextBufferEmpty() {
    return textBuffer.isEmpty();
  }

  public void appendText(final String text) {
    if (StringUtils.isNotEmpty(text)) {
      textBuffer.append(text);
    }
  }

  public String completeTextMessage() {
    finalAnswer = textBuffer.toString();
    return finalAnswer;
  }

  public void resetTextMessage() {
    currentTextMessageId = null;
    textBuffer.setLength(0);
  }

  public String finalAnswer() {
    return finalAnswer;
  }

  public boolean hasOpenReasoning() {
    return currentReasoningId != null;
  }

  public String startNextReasoning() {
    currentReasoningId = stableReplayId("reasoning-", currentSourceEventId, ++reasoningSequence);
    return currentReasoningId;
  }

  public String currentReasoningId() {
    return currentReasoningId;
  }

  public boolean hasOpenReasoningMessage() {
    return currentReasoningMessageId != null;
  }

  public String startNextReasoningMessage() {
    currentReasoningMessageId = stableReplayId("reasoning-msg-", currentSourceEventId, ++reasoningMessageSequence);
    return currentReasoningMessageId;
  }

  public String currentReasoningMessageId() {
    return currentReasoningMessageId;
  }

  public void closeReasoningMessage() {
    currentReasoningMessageId = null;
  }

  public void closeReasoning() {
    currentReasoningId = null;
    currentReasoningMessageId = null;
  }

  public void rememberToolCallParentStep(final String callId) {
    toolCallParentSteps.put(callId, currentStepName);
  }

  public String consumeToolCallParentStep(final String callId) {
    return toolCallParentSteps.remove(callId);
  }

  public long currentSourceTimestamp() {
    return currentSourceTimestamp;
  }

  public String sessionId() {
    return sessionId;
  }

  public String agentId() {
    return agentId;
  }

  private static String stableReplayId(final String prefix, final String sourceEventId, final int sequence) {
    if (StringUtils.isNotBlank(sourceEventId)) {
      return prefix + sourceEventId + "-" + sequence;
    }
    return prefix + sequence;
  }
}
