package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class EventUtilsTest {

  @Test
  void shouldTreatFinalResponseEventsAsTerminal() {
    final Event event = Event.builder().content(Content.builder().role("model").parts(List.of(Part.fromText("done"))).build()).build();

    assertThat(EventUtils.isTerminal(event)).isTrue();
  }

  @Test
  void shouldTreatEndInvocationEventsAsTerminal() {
    final Event event = Event.builder().actions(EventActions.builder().endInvocation(true).build()).content(Content.builder().role("model")
        .parts(List.of(Part.builder().functionCall(FunctionCall.builder().name("search").args(Map.of("query", "weather")).build()).build()))
        .build()).build();

    assertThat(EventUtils.isTerminal(event)).isTrue();
  }

  @Test
  void shouldNotTreatToolCallOnlyEventAsTerminal() {
    final Event event = Event.builder().content(Content.builder().role("model")
        .parts(List.of(Part.builder().functionCall(FunctionCall.builder().name("search").args(Map.of("query", "weather")).build()).build()))
        .build()).build();

    assertThat(EventUtils.isTerminal(event)).isFalse();
  }

  @Test
  void shouldReturnLatestDeltaValueForKey() {
    final Event older = Event.builder().id("event-1")
        .actions(EventActions.builder().stateDelta(new ConcurrentHashMap<>(Map.of("k", "older"))).build()).build();
    final Event unrelated = Event.builder().id("event-2")
        .actions(EventActions.builder().stateDelta(new ConcurrentHashMap<>(Map.of("x", "value"))).build()).build();
    final Event latest = Event.builder().id("event-3")
        .actions(EventActions.builder().stateDelta(new ConcurrentHashMap<>(Map.of("k", "latest"))).build()).build();

    final Object value = EventUtils.latestDeltaValue(List.of(older, unrelated, latest), "k");

    assertThat(value).isEqualTo("latest");
  }

  @Test
  void shouldReturnNullWhenKeyNotPresentInDelta() {
    final Event event = Event.builder().id("event-1")
        .actions(EventActions.builder().stateDelta(new ConcurrentHashMap<>(Map.of("x", "value"))).build()).build();

    final Object value = EventUtils.latestDeltaValue(List.of(event), "k");

    assertThat(value).isNull();
  }

  @Test
  void shouldTreatRemovedDeltaValueAsNull() {
    final Event removed = Event.builder().id("event-1")
        .actions(EventActions.builder().stateDelta(new ConcurrentHashMap<>(Map.of("k", State.REMOVED))).build()).build();

    final Object value = EventUtils.latestDeltaValue(List.of(removed), "k");

    assertThat(value).isNull();
  }
}
