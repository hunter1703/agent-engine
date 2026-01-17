package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.HybridEngine;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.EngineConfig;
import com.agentengine.engine.beans.config.ToolsConfig;
import com.agentengine.engine.message.Message;
import com.alibaba.fastjson2.JSONFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HybridAgentBuilderTest {

  @TempDir Path tempDir;

  @Test
  void buildCreatesHybridEngineWithDefaultPrompt() throws Exception {
    JSONFactory.getDefaultObjectReaderProvider()
        .addAutoTypeAccept("com.agentengine.engine.beans.config.");

    Path reasoningModel = tempDir.resolve("reasoning.json");
    Path toolModel = tempDir.resolve("tool.json");

    Files.writeString(
        reasoningModel,
        """
        {
          "provider": "OPEN_AI",
          "model": "gpt",
          "base_url": "http://localhost",
          "response_format": "text",
          "context_config": {
            "@type": "com.agentengine.engine.beans.config.LastNContextConfig",
            "keep_last": 1
          }
        }
        """);
    Files.writeString(
        toolModel,
        """
        {
          "provider": "OLLAMA",
          "model": "llama",
          "base_url": "http://localhost",
          "response_format": "text",
          "context_config": {
            "@type": "com.agentengine.engine.beans.config.LastNContextConfig",
            "keep_last": 1
          }
        }
        """);

    AgentConfig agentConfig = AgentConfig.empty();
    EngineConfig engine = agentConfig.getEngine();
    engine.setReasoning(reasoningModel.toString());
    engine.setTool(toolModel.toString());
    engine.setPrompt("");

    ToolsConfig tools = new ToolsConfig();
    tools.setEnabled(List.of("fake"));
    tools.setConfigs(Map.of("fake", Map.of("prefix", "ok-")));
    agentConfig.setTools(tools);

    HybridAgentBuilder builder = new HybridAgentBuilder();
    HybridEngine engineInstance = builder.build("test-agent", agentConfig);

    List<Message> prompt = engineInstance.buildPrompt("session");

    assertThat(prompt)
        .anyMatch(message -> "You are a helpful assistant.".equals(message.getContent()));
  }

  @Test
  void agentNamesDefaultsToNull() {
    HybridAgentBuilder builder = new HybridAgentBuilder();

    assertThat(builder.agentNames()).isNull();
  }
}
