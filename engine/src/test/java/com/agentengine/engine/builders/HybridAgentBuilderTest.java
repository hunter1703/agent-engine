package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.builders.agent.HybridAgentBuilder;
import com.agentengine.engine.builders.agent.PlanningAgentBuilder;
import com.agentengine.engine.builders.messagestore.MessageStoreProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryMessageStore;
import com.agentengine.engine.tools.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridAgentBuilderTest {

  @Test
  void buildCreatesHybridAgent() {
    final ModelProvider modelProvider = mock(ModelProvider.class);
    final PlanningAgentBuilder planningAgentBuilder = mock(PlanningAgentBuilder.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final MessageStoreProvider messageStoreProvider = mock(MessageStoreProvider.class);
    final InMemoryMessageStore messageStore = new InMemoryMessageStore();
    final InMemorySessionStore sessionStore = new InMemorySessionStore();

    final LLMModel model = new StubModel();
    when(modelProvider.get(anyString(), any(), any())).thenReturn(model);
    when(planningAgentBuilder.build(any(), any())).thenReturn(new PlanningAgent(model));
    when(messageStoreProvider.get(any())).thenReturn(messageStore);
    when(sessionServiceProvider.get(any())).thenReturn(sessionStore);

    final HybridAgentBuilder builder = new HybridAgentBuilder(modelProvider, planningAgentBuilder, new ToolRegistry(),
            sessionServiceProvider, messageStoreProvider);

    final HybridAgentConfig config = new HybridAgentConfig();
    config.setAgentId("test-agent");
    config.setModel(modelConfig("reasoner"));
    config.setRouterModel(modelConfig("router"));
    config.setPlanningModel(modelConfig("planner"));

    final HybridAgent agent = builder.build(config);

    assertThat(agent).isNotNull();
    assertThat(builder.type()).isEqualTo("hybrid");
  }

  private static AgentModelConfig modelConfig(final String id) {
    final AgentModelConfig config = new AgentModelConfig();
    config.setModelId(id);
    return config;
  }

  private static final class StubModel implements LLMModel {
    private final ContextManager contextManager = new BaseContextManager("reasoning", new InMemoryMessageStore(),
        "system", "protocol", List.of());

    @Override
    public Message generate(final List<Message> messages) {
      return Message.assistant("");
    }

    @Override
    public ResponseFormatType responseFormat() {
      return ResponseFormatType.JSON;
    }

    @Override
    public boolean thoughtsEnabled() {
      return false;
    }

    @Override
    public String thoughtsStartTag() {
      return "<think>";
    }

    @Override
    public String thoughtsEndTag() {
      return "</think>";
    }

    @Override
    public ContextManager getContextManager() {
      return contextManager;
    }
  }
}
