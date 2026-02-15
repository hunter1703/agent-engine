package com.agentengine.engine.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class ModelUtilsTest {

  @Test
  void generateServerConfig_setsCorrectValuesForOpenAiCompatible() {
    ModelConfig config = new ModelConfig();
    config.setType(ModelConfig.Provider.OPEN_AI_COMPATIBLE.type());
    config.setModel("test-model-path");

    boolean result = ModelUtils.generateServerConfig(config);

    assertTrue(result);
    assertNotNull(config.getBaseUrl());
    assertTrue(config.getBaseUrl().startsWith("http://127.0.0.1:"));
    assertTrue(config.getBaseUrl().endsWith("/v1"));

    assertEquals("/opt/homebrew/bin/llama-server", config.getServerCommand());

    assertNotNull(config.getServerArgs());
    assertTrue(config.getServerArgs().contains("-m"));
    assertTrue(config.getServerArgs().contains("test-model-path"));
    assertTrue(config.getServerArgs().contains("--host"));
    assertTrue(config.getServerArgs().contains("127.0.0.1"));
    assertTrue(config.getServerArgs().contains("--port"));

    // Extract the port from the base URL
    String baseUrl = config.getBaseUrl();
    String portStr = baseUrl.replace("http://127.0.0.1:", "").replace("/v1", "");
    int port = Integer.parseInt(portStr);
    assertTrue(port > 18000);

    // Check that the port in server args matches the one in the base URL
    List<String> serverArgs = config.getServerArgs();
    int portIndex = serverArgs.indexOf("--port");
    if (portIndex != -1 && portIndex + 1 < serverArgs.size()) {
      int serverPort = Integer.parseInt(serverArgs.get(portIndex + 1));
      assertEquals(port, serverPort);
    }
  }

  @Test
  void generateServerConfig_returnsFalseForNonOpenAiCompatible() {
    ModelConfig config = new ModelConfig();
    config.setType(ModelConfig.Provider.OLLAMA.type());

    boolean result = ModelUtils.generateServerConfig(config);

    assertFalse(result);
    assertNull(config.getBaseUrl());
    assertNull(config.getServerCommand());
    assertNull(config.getServerArgs());
  }

  @Test
  void generateServerConfig_handlesNullModelConfig() {
    boolean result = ModelUtils.generateServerConfig(null);

    assertFalse(result);
  }

  @Test
  void generateServerConfig_doesNotOverrideExplicitServerConfig() {
    final ModelConfig config = new ModelConfig();
    config.setType(ModelConfig.Provider.OPEN_AI_COMPATIBLE.type());
    config.setModel("qwen2.5-1.5b-instruct");
    config.setBaseUrl("http://127.0.0.1:19000/v1");
    config.setServerCommand("/custom/llama-server");
    config.setServerArgs(List.of("--host", "127.0.0.1", "--port", "19000"));

    final boolean result = ModelUtils.generateServerConfig(config);

    assertFalse(result);
    assertEquals("http://127.0.0.1:19000/v1", config.getBaseUrl());
    assertEquals("/custom/llama-server", config.getServerCommand());
    assertEquals(List.of("--host", "127.0.0.1", "--port", "19000"), config.getServerArgs());
  }
}
