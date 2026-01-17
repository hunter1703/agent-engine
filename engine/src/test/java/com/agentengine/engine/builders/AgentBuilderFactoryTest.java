package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentBuilderFactoryTest {

  @Test
  void returnsNamedBuilderOrFallback() {
    AgentBuilder named = new StubBuilder("alpha");
    AgentBuilder other = new StubBuilder("beta");
    Instance<AgentBuilder> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(named, other));
    HybridAgentBuilder fallback = mock(HybridAgentBuilder.class);

    AgentBuilderFactory factory = new AgentBuilderFactory(instance, fallback);

    assertThat(factory.getBuilder("alpha")).isSameAs(named);
    assertThat(factory.getBuilder("missing")).isSameAs(fallback);
  }

  private static final class StubBuilder implements AgentBuilder {
    private final String name;

    private StubBuilder(final String name) {
      this.name = name;
    }

    @Override
    public com.agentengine.engine.AgentEngine build(
        final String agentName, final com.agentengine.engine.beans.config.AgentConfig agentConfig) {
      return null;
    }

    @Override
    public List<String> agentNames() {
      return List.of(name);
    }
  }
}
