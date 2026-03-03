package com.agentengine.engine.api.beans.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.utils.JsonUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionInfoTest {

  @Test
  void setEventsUpdatesEventsJson() {
    final SessionInfo info = new SessionInfo();
    final List<Map<String, Object>> events = List.of(Map.of("id", "event-1"));

    info.setEvents(events);

    assertThat(info.getEventsJson()).isEqualTo(JsonUtils.toJson(events));
  }

  @Test
  void setEventsJsonHandlesInvalidPayload() {
    final SessionInfo info = new SessionInfo();

    info.setEventsJson("enc::not-json");

    assertThat(info.getEvents()).isEmpty();
    assertThat(info.getEventsJson()).isEqualTo("[]");
  }
}
