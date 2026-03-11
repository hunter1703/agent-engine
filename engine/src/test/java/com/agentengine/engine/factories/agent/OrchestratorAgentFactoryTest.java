package com.agentengine.engine.factories.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.factories.model.ModelProvider;
import com.agentengine.engine.model.AbstractLLM;
import com.agentengine.engine.tools.DiscoveredToolProviders;
import com.agentengine.engine.tools.ToolCatalogImpl;
import com.agentengine.engine.tools.ToolFactory;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrchestratorAgentFactoryTest {

  @Test
  void shouldBuildManagerWithoutTransferTargetsAndWithSubAgentTools() {
    final TestFixtures fixtures = new TestFixtures();
    final OrchestratorAgentFactory factory = fixtures.createFactory();
    final OrchestratorAgentConfig config = fixtures.createConfig(OrchestrationMode.MANAGER);

    final DelegatedAgent agent = (DelegatedAgent) factory.build(config);
    final LlmAgent llmAgent = extractDelegatedLlmAgent(agent);

    assertThat(agent.subAgents()).isEmpty();
    assertThat(llmAgent.disallowTransferToParent()).isTrue();
    assertThat(llmAgent.disallowTransferToPeers()).isTrue();
    assertThat(llmAgent.tools().blockingGet()).extracting(com.google.adk.tools.BaseTool::name)
        .containsExactly("sub-agent");
  }

  @Test
  void shouldBuildTransferWithTransferTargetsAndSubAgentTools() {
    final TestFixtures fixtures = new TestFixtures();
    final OrchestratorAgentFactory factory = fixtures.createFactory();
    final OrchestratorAgentConfig config = fixtures.createConfig(OrchestrationMode.TRANSFER);

    final DelegatedAgent agent = (DelegatedAgent) factory.build(config);
    final LlmAgent llmAgent = extractDelegatedLlmAgent(agent);

    assertThat(agent.subAgents()).extracting(BaseAgent::name).containsExactly("sub-agent");
    assertThat(llmAgent.disallowTransferToParent()).isFalse();
    assertThat(llmAgent.disallowTransferToPeers()).isFalse();
    assertThat(llmAgent.tools().blockingGet()).isEmpty();
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

  private static final class TestFixtures {
    private final DefaultAgentConfig subAgentConfig;
    private final Agent subAgent;

    private TestFixtures() {
      this.subAgentConfig = new DefaultAgentConfig();
      subAgentConfig.setId("sub-agent");
      this.subAgent = new StubAgent("sub-agent", subAgentConfig);
    }

    private OrchestratorAgentFactory createFactory() {
      final ModelProvider modelProvider = mock(ModelProvider.class);
      when(modelProvider.acquire("model-1")).thenReturn(new StubModel("model-1"));

      @SuppressWarnings("unchecked")
      final Instance<com.agentengine.engine.api.tools.ToolProvider> toolProviders =
          mock(Instance.class);
      when(toolProviders.iterator()).thenReturn(Collections.emptyIterator());
      @SuppressWarnings("unchecked")
      final Instance<com.agentengine.engine.api.tools.ToolsetProvider> toolsetProviders =
          mock(Instance.class);
      when(toolsetProviders.iterator()).thenReturn(Collections.emptyIterator());
      final ToolCatalogImpl toolCatalogImpl =
          new ToolCatalogImpl(toolProviders, toolsetProviders, emptyDiscoveredToolProviders());
      final ToolFactory toolFactory =
          new ToolFactory(toolCatalogImpl);

      @SuppressWarnings("unchecked")
      final Instance<AgentProvider> agentProviderInstance = mock(Instance.class);
      final AgentProvider agentProvider = mock(AgentProvider.class);
      when(agentProviderInstance.get()).thenReturn(agentProvider);
      when(agentProvider.create(subAgentConfig)).thenReturn(subAgent);

      final AgentService agentService = mock(AgentService.class);
      when(agentService.getAgent("sub-agent")).thenReturn(Optional.of(subAgentConfig));

      return new OrchestratorAgentFactory(
          modelProvider, toolFactory, agentProviderInstance, agentService);
    }

    @SuppressWarnings("unchecked")
    private static DiscoveredToolProviders emptyDiscoveredToolProviders() {
      final Instance<com.agentengine.engine.api.tools.Tool> tools = mock(Instance.class);
      when(tools.iterator()).thenReturn(List.<com.agentengine.engine.api.tools.Tool>of().iterator());
      return new DiscoveredToolProviders(tools);
    }

    private OrchestratorAgentConfig createConfig(final OrchestrationMode mode) {
      final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
      config.setId("manager-agent");
      config.setModelId("model-1");
      config.setSystemPrompt("Route work through tools.");
      config.setTools(List.of());
      config.setSubAgentIds(List.of("sub-agent"));
      config.setOrchestrationMode(mode.name());
      return config;
    }
  }

  private static final class StubAgent extends Agent {
    private StubAgent(final String name, final DefaultAgentConfig config) {
      super(name, name, List.of(), config, List.of(), List.of());
    }

    @Override
    protected Flowable<Event> runAsyncImpl(final InvocationContext invocationContext) {
      return Flowable.empty();
    }

    @Override
    protected Flowable<Event> runLiveImpl(final InvocationContext invocationContext) {
      return Flowable.empty();
    }
  }

  private static final class StubModel extends AbstractLLM {
    private StubModel(final String modelId) {
      super(modelId, Parser.create());
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
