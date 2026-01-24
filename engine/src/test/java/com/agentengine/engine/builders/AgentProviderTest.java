package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.agent.HybridAgentBuilder;
import jakarta.enterprise.inject.Instance;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentProviderTest {

  @Test
  void returnsNamedBuilderOrFallback() {
    AgentBuilder named = new StubBuilder("alpha");
    AgentBuilder other = new StubBuilder("beta");
    @SuppressWarnings("unchecked")
    Instance<AgentBuilder> instance = (Instance<AgentBuilder>) mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(named, other));
    HybridAgentBuilder fallback = mock(HybridAgentBuilder.class);
    Agent fallbackAgent = mock(Agent.class);
    when(fallback.build(any())).thenReturn(fallbackAgent);

    AgentProvider factory = new AgentProvider(instance, fallback);

    AgentConfig config = new AgentConfig();
    config.setType("alpha");
    assertThat(factory.get(config)).isNotNull();

    AgentConfig missing = new AgentConfig();
    missing.setType("missing");
    assertThat(factory.get(missing)).isSameAs(fallbackAgent);
  }

  private static final class StubBuilder implements AgentBuilder<AgentConfig, Agent> {
    private final String name;

    private StubBuilder(final String name) {
      this.name = name;
    }

    @Override
    public Agent build(final AgentConfig agentConfig) {
      return mock(Agent.class);
    }

    @Override
    public String type() {
      return name;
    }
  }
}
