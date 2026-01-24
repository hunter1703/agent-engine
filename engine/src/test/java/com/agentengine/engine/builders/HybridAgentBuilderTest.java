package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.builders.agent.HybridAgentBuilder;
import org.junit.jupiter.api.Test;

class HybridAgentBuilderTest {

  @Test
  void buildThrowsOnMismatchedConfig() {
    ConfigRepository repository = mock(ConfigRepository.class);
    HybridAgentBuilder builder = new HybridAgentBuilder(repository);

    AgentConfig config = new AgentConfig();
    config.setEngine(mock(EngineConfig.class));

    assertThatThrownBy(() -> builder.build("test", config)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Hybrid engine requires hybrid agent config");
  }

  @Test
  void buildCreatesFunctionalAgent() {
    ConfigRepository repository = mock(ConfigRepository.class);
    HybridAgentBuilder builder = new HybridAgentBuilder(repository);

    HybridAgentConfig hybridConfig = new HybridAgentConfig();
    hybridConfig.setReasoningModelId("reasoner-id");
    hybridConfig.setRouterModelId("tool-id");

    AgentConfig agentConfig = new AgentConfig();
    agentConfig.setEngine(hybridConfig);
    agentConfig.setTools(new ToolsConfig());

    ModelConfig modelConfig = new ModelConfig();
    modelConfig.setType("OPEN_AI");
    modelConfig.setModel("gpt-4");
    modelConfig.setResponseFormat("text");

    when(repository.loadModelConfig(anyString())).thenReturn(modelConfig);

    HybridAgent agent = builder.build("test-agent", agentConfig);

    assertThat(agent).isNotNull();
    assertThat(builder.type()).isEqualTo("hybrid");
  }

  @Test
  void buildHandlesJsonFormatAndThoughts() {
    ConfigRepository repository = mock(ConfigRepository.class);
    HybridAgentBuilder builder = new HybridAgentBuilder(repository);

    HybridAgentConfig hybridConfig = new HybridAgentConfig();
    hybridConfig.setReasoningModelId("reasoner-id");
    hybridConfig.setRouterModelId("tool-id");

    AgentConfig agentConfig = new AgentConfig();
    agentConfig.setEngine(hybridConfig);

    ModelConfig jsonConfig = new ModelConfig();
    jsonConfig.setType("OPEN_AI");
    jsonConfig.setModel("gpt-4");
    jsonConfig.setResponseFormat("json");
    jsonConfig.setThoughtsEnabled(true);

    when(repository.loadModelConfig(anyString())).thenReturn(jsonConfig);

    HybridAgent agent = builder.build("json-agent", agentConfig);
    assertThat(agent).isNotNull();
  }
}
