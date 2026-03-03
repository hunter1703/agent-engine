package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.Event;
import org.junit.jupiter.api.Test;

class EventUtilsTest {

  @Test
  void marksInternalWhenActionsMissing() {
    final Event event = Event.builder()
        .id("event-1")
        .invocationId("inv-1")
        .author("user")
        .build();

    EventUtils.markAsInternal(event);

    assertThat(EventUtils.isInternal(event)).isTrue();
    assertThat(event.actions()).isNotNull();
    assertThat(event.actions().stateDelta())
        .containsEntry(EventUtils.INTERNAL_KEY, Boolean.TRUE);
  }
}
