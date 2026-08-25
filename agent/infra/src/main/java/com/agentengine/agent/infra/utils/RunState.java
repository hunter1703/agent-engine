package com.agentengine.agent.infra.utils;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.events.Event;
import com.google.genai.types.FunctionCall;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RunState {

  private final List<ToolCallSignature> lastToolCalls = new ArrayList<>();
  // Set and consumed within the same BaseFlow.runLoop iteration, always before that turn's
  // events are committed — buildFrom can never observe this as true, so resetting it isn't
  // just low-risk like the other transient fields, it's a genuine no-op.
  private boolean continuationRequested;
  private int offTopicRetries;
  private int turnsUsed;
  private final List<Violation> violations = new ArrayList<>();
  private PendingAnswer pendingAnswer;
  private PendingNote pendingNote;

  public RunState() {}

  /**
   * Reconstructs only essential fields (lastToolCalls) from the session event log, not everything
   * (violations, offTopicRetries, turnsUsed, continuationRequested, etc.)
   */
  public static RunState buildFrom(final List<Event> events) {
    if (CollectionUtils.isEmpty(events)) {
      return new RunState();
    }
    final RunState state = new RunState();
    state.updateLastToolCalls(readLastToolCalls(events));
    return state;
  }

  private static List<ToolCallSignature> readLastToolCalls(final List<Event> events) {
    for (int i = events.size() - 1; i >= 0; i--) {
      final List<FunctionCall> calls = events.get(i).functionCalls();
      if (!calls.isEmpty()) {
        return calls.stream()
            .map(
                functionCall ->
                    new ToolCallSignature(
                        functionCall.name().orElse(null), functionCall.args().orElse(Map.of())))
            .toList();
      }
    }
    return List.of();
  }

  public List<ToolCallSignature> lastToolCalls() {
    return List.copyOf(lastToolCalls);
  }

  public List<Violation> violations() {
    return List.copyOf(violations);
  }

  public void updateLastToolCalls(final List<ToolCallSignature> toolCalls) {
    lastToolCalls.clear();
    if (CollectionUtils.isNotEmpty(toolCalls)) {
      lastToolCalls.addAll(toolCalls);
    }
  }

  public void addViolation(final Violation violation) {
    if (violation == null) {
      return;
    }
    violations.removeIf(existing -> Objects.equals(existing.code(), violation.code()));
    violations.add(violation);
  }

  public void addViolations(final List<Violation> violations) {
    if (violations == null) {
      return;
    }
    for (final Violation violation : violations) {
      addViolation(violation);
    }
  }

  public void clearViolations() {
    violations.clear();
  }

  public int incrementOffTopicRetries() {
    offTopicRetries += 1;
    return offTopicRetries;
  }

  public void resetOffTopicRetries() {
    offTopicRetries = 0;
  }

  public boolean consumeTurn(final int limit) {
    return ++turnsUsed <= limit;
  }

  public void requestContinuation(Violation violation) {
    addViolation(violation);
    this.continuationRequested = true;
  }

  public boolean consumeContinuation() {
    final boolean was = continuationRequested;
    continuationRequested = false;
    return was;
  }

  public void enterAnswerMode(final String saveMessage, final long minSaveTokens) {
    this.pendingAnswer = new PendingAnswer(saveMessage, minSaveTokens);
  }

  public boolean isInAnswerMode() {
    return pendingAnswer != null;
  }

  public PendingAnswer consumeAnswerMode() {
    final PendingAnswer result = pendingAnswer;
    pendingAnswer = null;
    return result;
  }

  public void startNote(
      final String notebookId, final String noteTitle, final boolean continuation) {
    this.pendingNote = new PendingNote(notebookId, noteTitle, continuation);
  }

  public boolean isNoteStarted() {
    return pendingNote != null;
  }

  public PendingNote finishNote() {
    final PendingNote result = pendingNote;
    pendingNote = null;
    return result;
  }

  public record PendingAnswer(String saveMessage, long minSaveTokens) {}

  public record PendingNote(String notebookId, String noteTitle, boolean continuation) {}

  public record ToolCallSignature(String name, Map<String, Object> args) {
    public ToolCallSignature {
      name = StringUtils.isBlank(name) ? "unknown" : name;
      args = args == null ? Map.of() : Map.copyOf(new HashMap<>(args));
    }
  }
}
