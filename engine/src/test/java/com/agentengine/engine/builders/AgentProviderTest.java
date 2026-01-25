package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.agent.HybridAgentBuilder;
import jakarta.enterprise.inject.Instance;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentProviderTest {

  @Test
  void returnsNamedBuilderOrFallback() {
    final HybridAgent fallbackAgent = mock(HybridAgent.class);
    final AgentBuilder<AgentConfig, Agent> named = new StubBuilder("alpha");
    final AgentBuilder<AgentConfig, Agent> other = new StubBuilder("beta");
    @SuppressWarnings("unchecked")
    final Instance<AgentBuilder<?, ?>> instance = (Instance<AgentBuilder<?, ?>>) mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(named, other));
    final HybridAgentBuilder fallback = mock(HybridAgentBuilder.class);
    when(fallback.build(any())).then(invocation -> fallbackAgent);

    final AgentProvider factory = new AgentProvider(instance, fallback);

    final AgentConfig config = new AgentConfig();
    config.setType("alpha");
    assertThat((Object) factory.get(config)).isNotNull();

    // Use HybridAgentConfig for the fallback case since HybridAgentBuilder expects
    // it
    final HybridAgentConfig missing = new HybridAgentConfig();
    missing.setType("missing");
    assertThat((Object) factory.get(missing)).isSameAs(fallbackAgent);
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
