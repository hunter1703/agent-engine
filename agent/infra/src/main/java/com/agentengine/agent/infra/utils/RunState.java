package com.agentengine.agent.infra.utils;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.Event;
import com.google.genai.types.FunctionCall;
import java.util.*;

public final class RunState {

  private final List<ToolCallSignature> lastToolCalls = new ArrayList<>();
  private int offTopicRetries;
  private int turnsUsed;
  private final Set<Signal<?>> signals = new HashSet<>();
  private PendingAnswer pendingAnswer;
  private PendingNote pendingNote;

  public RunState() {}

  /**
   * Reconstructs only essential fields (lastToolCalls) from the session event log, not everything
   * (signals, offTopicRetries, turnsUsed, etc.)
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

  public Set<Signal<?>> signals() {
    return Set.copyOf(signals);
  }

  public void updateLastToolCalls(final List<ToolCallSignature> toolCalls) {
    lastToolCalls.clear();
    if (CollectionUtils.isNotEmpty(toolCalls)) {
      lastToolCalls.addAll(toolCalls);
    }
  }

  public void addSignal(final Signal<?> signal) {
    if (signal == null) {
      return;
    }
    signals.add(signal);
  }

  public void clearSignals() {
    signals.clear();
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

  /**
   * Whether any currently-queued signal wants one more run loop iteration even if the model's
   * response otherwise looked final.
   */
  public boolean continuationRequested() {
    return signals.stream().anyMatch(Signal::requiresContinuation);
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

  public void startNote(final String notebookId, final String noteTitle) {
    this.pendingNote = new PendingNote(notebookId, noteTitle);
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

  public record PendingNote(String notebookId, String noteTitle) {}

  public record ToolCallSignature(String name, Map<String, Object> args) {
    public ToolCallSignature {
      name = StringUtils.isBlank(name) ? "unknown" : name;
      args = args == null ? Map.of() : Map.copyOf(new HashMap<>(args));
    }
  }
}
