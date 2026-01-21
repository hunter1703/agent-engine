package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.AgentBuilderFactoryImpl;
import com.agentengine.engine.api.builders.AgentBuilderFactory;
import jakarta.enterprise.inject.Instance;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentBuilderFactoryTest {

  @Test
  void returnsNamedBuilderOrFallback() {
    AgentBuilder named = new StubBuilder("alpha");
    AgentBuilder other = new StubBuilder("beta");
    @SuppressWarnings("unchecked")
    Instance<AgentBuilder> instance = (Instance<AgentBuilder>) mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(named, other));
    HybridAgentBuilder fallback = mock(HybridAgentBuilder.class);

    AgentBuilderFactory factory = new AgentBuilderFactoryImpl(instance, fallback);

    assertThat(factory.getBuilder("alpha")).isSameAs(named);
    assertThat(factory.getBuilder("missing")).isSameAs(fallback);
  }

  private static final class StubBuilder implements AgentBuilder {
    private final String name;

    private StubBuilder(final String name) {
      this.name = name;
    }

    @Override
    public Agent build(final String agentName, final AgentConfig agentConfig) {
      return null;
    }

    @Override
    public String type() {
      return name;
    }
  }
}
