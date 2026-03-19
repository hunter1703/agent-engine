package com.agentengine.engine.factories.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.factories.model.ModelProvider;
import com.agentengine.engine.model.AbstractLLM;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.plugin.tools.ToolProvider;
import com.agentengine.engine.plugin.tools.ToolsetProvider;
import com.agentengine.engine.tools.DiscoveredToolProviders;
import com.agentengine.engine.tools.ToolFactory;
import com.agentengine.engine.tools.ToolServiceImpl;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultAgentFactoryTest {

  @Test
  void shouldIncludeHumanInTheLoopToolWhenAgentIsResumable() {
    final DelegatedAgent agent = new DefaultAgentFactory(createModelProvider(), createToolFactory()).build(createConfig(true));

    assertThat(extractDelegatedLlmAgent(agent).tools().blockingGet()).extracting(com.google.adk.tools.BaseTool::name)
        .containsExactly("human_in_the_loop");
  }

  @Test
  void shouldNotIncludeHumanInTheLoopToolWhenAgentIsNotResumable() {
    final DelegatedAgent agent = new DefaultAgentFactory(createModelProvider(), createToolFactory()).build(createConfig(false));

    assertThat(extractDelegatedLlmAgent(agent).tools().blockingGet()).isEmpty();
  }

  private static DefaultAgentConfig createConfig(final boolean resumable) {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("default-agent");
    config.setModelId("model-1");
    config.setSystemPrompt("Answer plainly.");
    config.setTools(List.of());
    config.getRuntime().setResumable(resumable);
    return config;
  }

  private static ModelProvider createModelProvider() {
    final ModelProvider modelProvider = mock(ModelProvider.class);
    when(modelProvider.acquire("model-1")).thenReturn(new StubModel("model-1"));
    return modelProvider;
  }

  @SuppressWarnings("unchecked")
  private static ToolFactory createToolFactory() {
    final Instance<ToolProvider> toolProviders = mock(Instance.class);
    when(toolProviders.iterator()).thenReturn(Collections.emptyIterator());
    final Instance<ToolsetProvider> toolsetProviders = mock(Instance.class);
    when(toolsetProviders.iterator()).thenReturn(Collections.emptyIterator());
    final ToolServiceImpl toolServiceImpl = new ToolServiceImpl(toolProviders, toolsetProviders, emptyDiscoveredToolProviders());
    return new ToolFactory(toolServiceImpl);
  }

  @SuppressWarnings("unchecked")
  private static DiscoveredToolProviders emptyDiscoveredToolProviders() {
    final Instance<Tool> tools = mock(Instance.class);
    when(tools.iterator()).thenReturn(List.<Tool>of().iterator());
    return new DiscoveredToolProviders(tools);
  }

  private static LlmAgent extractDelegatedLlmAgent(final DelegatedAgent agent) {
    try {
      final Field field = DelegatedAgent.class.getDeclaredField("delegated");
      field.setAccessible(true);
      return (LlmAgent) field.get(agent);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Failed to access delegated agent.", ex);
    }
  }

  private static final class StubModel extends AbstractLLM {
    private StubModel(final String modelId) {
      super(modelId, new Parser("", true));
    }

    @Override
    public Flowable<LlmResponse> generateContent(final LlmRequest request, final boolean stream) {
      return Flowable.empty();
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest request) {
      return null;
    }
  }
}
