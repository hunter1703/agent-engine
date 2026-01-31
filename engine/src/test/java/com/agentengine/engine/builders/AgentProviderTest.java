package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.agents.SimpleAgent;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.agent.SimpleAgentBuilder;
import com.google.adk.agents.LlmAgent;
import jakarta.enterprise.inject.Instance;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentProviderTest {

  @Test
  void returnsNamedBuilderOrFallback() {
    final SimpleAgent fallbackAgent = mock(SimpleAgent.class);
    final AgentBuilder<AgentConfig, LlmAgent> named = new StubBuilder("alpha");
    final AgentBuilder<AgentConfig, LlmAgent> other = new StubBuilder("beta");
    @SuppressWarnings("unchecked")
    final Instance<AgentBuilder<?, ?>> instance = (Instance<AgentBuilder<?, ?>>) mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.<AgentBuilder<?, ?>>of(named, other));
    final SimpleAgentBuilder fallback = mock(SimpleAgentBuilder.class);
    when(fallback.build(any())).then(invocation -> fallbackAgent);

    final AgentProvider factory = new AgentProvider(instance, fallback);

    final AgentConfig config = new AgentConfig();
    config.setType("alpha");
    final LlmAgent agent = factory.get(config);
    assertThat(agent).isNotNull();

    final AgentConfig missing = new AgentConfig();
    missing.setType("missing");
    final LlmAgent missingAgent = factory.get(missing);
    assertThat(missingAgent).isSameAs(fallbackAgent);
  }

  private static final class StubBuilder implements AgentBuilder<AgentConfig, LlmAgent> {
    private final String name;

    private StubBuilder(final String name) {
      this.name = name;
    }

    @Override
    public LlmAgent build(final AgentConfig agentConfig) {
      return mock(LlmAgent.class);
    }

    @Override
    public String type() {
      return name;
    }
  }
}
