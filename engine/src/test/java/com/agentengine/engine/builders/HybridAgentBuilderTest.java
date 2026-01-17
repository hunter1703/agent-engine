package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.HybridEngine;
import com.agentengine.engine.MongoConfigRepository;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.HybridEngineConfig;
import com.agentengine.engine.beans.config.LastNContextConfig;
import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.beans.config.ToolsConfig;
import com.agentengine.engine.message.Message;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HybridAgentBuilderTest {

  @Test
  void buildCreatesHybridEngineWithDefaultPrompt() throws Exception {
    MongoConfigRepository repository = mock(MongoConfigRepository.class);
    ModelConfig reasoningModel = buildModelConfig("OPEN_AI", "gpt", "http://localhost");
    ModelConfig toolModel = buildModelConfig("OLLAMA", "llama", "http://localhost");
    when(repository.loadModelConfig("reasoner")).thenReturn(reasoningModel);
    when(repository.loadModelConfig("tool")).thenReturn(toolModel);

    AgentConfig agentConfig = AgentConfig.empty();
    HybridEngineConfig engine = (HybridEngineConfig) agentConfig.getEngine();
    engine.setReasoning("reasoner");
    engine.setTool("tool");
    engine.setSystemPrompt("");

    ToolsConfig tools = new ToolsConfig();
    tools.setEnabled(List.of("fake"));
    tools.setConfigs(Map.of("fake", Map.of("prefix", "ok-")));
    agentConfig.setTools(tools);

    HybridAgentBuilder builder = new HybridAgentBuilder(repository);
    HybridEngine engineInstance = builder.build("test-agent", agentConfig);

    List<Message> prompt = engineInstance.buildPrompt("session");

    assertThat(prompt).anyMatch(message -> "You are a helpful assistant.".equals(message.getContent()));
  }

  @Test
  void agentNamesDefaultsToNull() {
    HybridAgentBuilder builder = new HybridAgentBuilder(mock(MongoConfigRepository.class));

    assertThat(builder.type()).isNull();
  }

  private static ModelConfig buildModelConfig(final String provider, final String model, final String baseUrl) {
    ModelConfig config = new ModelConfig();
    config.setProvider(provider);
    config.setModel(model);
    config.setBaseUrl(baseUrl);
    config.setResponseFormat("text");
    LastNContextConfig contextConfig = new LastNContextConfig();
    contextConfig.setKeepLast(1);
    config.setContextConfig(contextConfig);
    return config;
  }
}
