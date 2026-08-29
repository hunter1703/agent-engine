package com.agentengine.agent.infra.utils;

import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
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
   * Reconstructs essential fields from the session event log: lastToolCalls, and any signal that
   * was raised (see {@link #addSignal(CallbackContext, Signal)}) but never actually delivered —
   * e.g. because the step it was raised on ended the turn (PAUSED/TERMINAL) before a next request
   * was ever built to carry it. Everything else (offTopicRetries, turnsUsed, etc.) is not
   * reconstructed, since it's meaningless once an invocation has already ended.
   */
  public static RunState buildFrom(final List<Event> events) {
    if (CollectionUtils.isEmpty(events)) {
      return new RunState();
    }
    final RunState state = new RunState();
    state.updateLastToolCalls(readLastToolCalls(events));
    state.addSignals(readUndeliveredSignals(events));
    return state;
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

  /**
   * Adds {@code signal} to the in-memory queue, and also records it against {@code callbackContext}
   * so it survives even if this run never gets a next request to deliver it in — a step that ends
   * the turn (PAUSED/TERMINAL) has no next {@code preprocess()} for {@code SignalProcessor} to run
   * in, so a signal queued only in memory would otherwise be silently lost. Because {@code
   * CallbackContext} shares its {@code EventActions} with whatever event this step's callback is
   * tied to, the record rides on an event that's persisted regardless of how the turn ends, and
   * {@link #buildFrom} re-queues it on the next invocation if it was never actually delivered.
   */
  public void addSignal(final CallbackContext callbackContext, final Signal<?> signal) {
    addSignal(signal);
    if (callbackContext == null || signal == null) {
      return;
    }
    final List<Signal<?>> raised =
        CollectionUtils.nullSafeMutableList(
            CollectionUtils.getListFromMap(callbackContext.state(), EventUtils.SIGNAL_RAISED_KEY));
    raised.add(signal);
    callbackContext.state().put(EventUtils.SIGNAL_RAISED_KEY, raised);
  }

  /**
   * Converts every queued signal into its delivery event(s), marks them delivered, clears the
   * queue, and returns the events — the whole job {@code SignalProcessor} needs done in one call,
   * since it has no reason to know the shape (violation vs. plain text) a signal turns into.
   */
  public List<Event> deliverSignals(final InvocationContext context) {
    if (CollectionUtils.isEmpty(signals)) {
      return List.of();
    }
    final List<Violation> violations = new ArrayList<>();
    final List<String> textUpdates = new ArrayList<>();
    for (final Signal<?> signal : signals) {
      if (signal.context() instanceof Violation violation) {
        violations.add(violation);
      } else {
        textUpdates.add(String.valueOf(signal.context()));
      }
    }

    final List<Event> events = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(violations)) {
      events.add(EventUtils.buildCorrectiveEvent(context, violations));
    }
    for (final String textUpdate : textUpdates) {
      events.add(EventUtils.buildUserTextEvent(context, textUpdate));
    }

    clearSignals(events);
    return events;
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

  /**
   * Clears the in-memory queue and stamps every event in {@code deliveryEvents} — the event(s)
   * {@code SignalProcessor} just built to carry the queued signals to the model — with the full set
   * of signals being cleared, so {@link #buildFrom} can tell a signal was actually delivered rather
   * than merely raised. Mirrors {@link #addSignal(CallbackContext, Signal)}'s "update memory and
   * durable state together" shape.
   */
  private void clearSignals(final List<Event> deliveryEvents) {
    final List<Signal<?>> delivered = List.copyOf(signals);
    for (final Event event : deliveryEvents) {
      markDelivered(event, delivered);
    }
    signals.clear();
  }

  private static void markDelivered(final Event event, final List<Signal<?>> delivered) {
    final EventActions actions = event.actions();
    final EventActions.Builder builder =
        actions == null ? EventActions.builder() : actions.toBuilder();
    final Map<String, Object> delta =
        CollectionUtils.nullSafeMutableMap(actions == null ? null : actions.stateDelta());
    delta.put(EventUtils.SIGNAL_DELIVERED_KEY, delivered);
    event.setActions(builder.stateDelta(delta).build());
  }

  private static Set<Signal<?>> readUndeliveredSignals(final List<Event> events) {
    final Map<String, Signal<?>> raised = new HashMap<>();
    for (final Event event : events) {
      final EventActions actions = event.actions();
      final Map<String, Object> delta = actions == null ? null : actions.stateDelta();
      if (CollectionUtils.isEmpty(delta)) {
        continue;
      }
      final List<Signal<?>> raisedSignals =
          CollectionUtils.nullSafeList(
              CollectionUtils.getListFromMap(delta, EventUtils.SIGNAL_RAISED_KEY));
      for (final Signal<?> signal : raisedSignals) {
        raised.put(signal.id(), signal);
      }

      final List<Signal<?>> deliveredSignals =
          CollectionUtils.nullSafeList(
              CollectionUtils.getListFromMap(delta, EventUtils.SIGNAL_DELIVERED_KEY));
      for (final Signal<?> signal : deliveredSignals) {
        raised.remove(signal.id());
      }
    }
    return new HashSet<>(raised.values());
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

  private void addSignals(final Collection<Signal<?>> signals) {
    this.signals.addAll(CollectionUtils.nullSafeList(signals));
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
