package com.agentengine.engine.api.utils;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.State;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Utilities for working with runtime {@link Event} objects. */
public final class EventUtils {

  /**
   * Key used to mark an event as internal (pipeline-only).
   *
   * <p>Uses the {@link State#TEMP_PREFIX} so the value is not merged into session state, but is
   * preserved in the event's {@code stateDelta} through serialisation round-trips.
   */
  public static final String INTERNAL_KEY = State.TEMP_PREFIX + "internal";

  private EventUtils() {}

  /**
   * Marks an event as internal so it is excluded from end-user-facing output (e.g. AG-UI events)
   * while remaining fully visible to the LLM as session history.
   */
  public static void markAsInternal(final Event event) {
    if (event == null) {
      return;
    }
    EventActions actions = event.actions();
    if (actions == null) {
      actions = new EventActions();
    }
    ConcurrentMap<String, Object> delta = actions.stateDelta();
    if (delta == null) {
      delta = new ConcurrentHashMap<>();
    }
    delta.put(INTERNAL_KEY, Boolean.TRUE);
    event.setActions(actions.toBuilder().stateDelta(delta).build());
  }

  /** Returns {@code true} if the event was marked as internal via {@link #markAsInternal}. */
  public static boolean isInternal(final Event event) {
    if (event == null || event.actions() == null || event.actions().stateDelta() == null) {
      return false;
    }
    return Boolean.TRUE.equals(
        CollectionUtils.getBooleanValueFromMap(event.actions().stateDelta(), INTERNAL_KEY));
  }
}
