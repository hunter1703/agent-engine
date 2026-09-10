package com.agentengine.agent.core.serializers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.events.SequencedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class MixinTest {
  @Test
  public void testMixin() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    SessionEvent se = new SessionEvent();
    se.setId("test-id");
    SequencedEvent<?> event = new SequencedEvent<>(1, se);

    String json = mapper.writeValueAsString(event);
    System.out.println("JSON: " + json);

    SequencedEvent<?> parsed = mapper.readValue(json, SequencedEvent.class);
    System.out.println("Parsed payload type: " + parsed.payload().getClass());

    assertThat(parsed.payload()).isInstanceOf(SessionEvent.class);

    // Also test with String payload
    SequencedEvent<?> stringEvent = new SequencedEvent<>(2, "test-string");
    String stringJson = mapper.writeValueAsString(stringEvent);
    System.out.println("String JSON: " + stringJson);
    SequencedEvent<?> parsedStringEvent = mapper.readValue(stringJson, SequencedEvent.class);
    assertThat(parsedStringEvent.payload()).isEqualTo("test-string");
  }
}
