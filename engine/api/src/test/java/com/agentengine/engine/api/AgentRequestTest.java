package com.agentengine.engine.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentRequestTest {

  @Test
  void withSessionIdCreatesNewInstanceWithUpdatedSessionId() {
    AgentRequest original = new AgentRequest();
    original.setType("INVOKE_AGENT");
    original.setAgentId("test-agent");
    original.setAgentConfigPath("config.json");
    original.setSessionId("original-session");
    original.setMessage("hello");

    AgentRequest modified = original.withSessionId("new-session");

    assertThat(modified).isNotSameAs(original);
    assertThat(modified.getSessionId()).isEqualTo("new-session");
    assertThat(modified.getType()).isEqualTo("INVOKE_AGENT");
    assertThat(modified.getAgentId()).isEqualTo("test-agent");
    assertThat(modified.getAgentConfigPath()).isEqualTo("config.json");
    assertThat(modified.getMessage()).isEqualTo("hello");
    assertThat(original.getSessionId()).isEqualTo("original-session");
  }
}
