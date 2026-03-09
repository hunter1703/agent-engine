package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.Test;

class TypedUtilsTest {

  @Test
  void shouldReturnSameInstanceWhenAlreadyTyped() {
    final SessionState sessionState = new SessionState();
    sessionState.setPaused(true);

    final SessionState resolved = TypedUtils.toType(sessionState, SessionState.class);

    assertThat(resolved).isSameAs(sessionState);
  }

  @Test
  void shouldConvertDocumentToTypedObject() {
    final Document raw = new Document("thinkingOpen", true).append("offTopicRetries", 1);

    final RunState resolved = TypedUtils.toType(raw, RunState.class);

    assertThat(resolved.isThinkingOpen()).isTrue();
    assertThat(resolved.getOffTopicRetries()).isEqualTo(1);
  }

  @Test
  void shouldReturnNullForUnknownType() {
    final Object raw = "not-a-map";

    final RunState resolved = TypedUtils.toType(raw, RunState.class);

    assertThat(resolved).isNull();
  }
}
