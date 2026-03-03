package com.agentengine.engine.builders.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.AgentConfig;
import org.junit.jupiter.api.Test;

class AgentNameResolverTest {

  @Test
  void usesValidDisplayName() {
    final AgentConfig config = new AgentConfig();
    config.setId("story_agent_test");
    config.setName("Story Agent Test");

    final String resolved = AbstractAgentBuilder.resolveAgentName(config);

    assertThat(resolved).isEqualTo("Story Agent Test");
  }

  @Test
  void fallsBackToIdWhenNameInvalid() {
    final AgentConfig config = new AgentConfig();
    config.setId("story_agent_test");
    config.setName("Story Agent Test (Planning)");

    final String resolved = AbstractAgentBuilder.resolveAgentName(config);

    assertThat(resolved).isEqualTo("story_agent_test");
  }

  @Test
  void usesDefaultWhenNameAndIdInvalid() {
    final AgentConfig config = new AgentConfig();
    config.setId("invalid(id)");
    config.setName("Story Agent Test (Planning)");

    final String resolved = AbstractAgentBuilder.resolveAgentName(config);

    assertThat(resolved).isEqualTo("agent");
  }
}
