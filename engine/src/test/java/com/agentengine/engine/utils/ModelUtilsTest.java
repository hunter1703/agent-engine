package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.ModelConfig;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelUtilsTest {

  @Test
  void shouldGenerateServerConfigWhenOpenAiCompatibleModelMissingServerSettings() {
    final ModelConfig model = new ModelConfig();
    model.setType("OPEN_AI_COMPATIBLE");
    model.setModel("/models/qwen.gguf");

    final boolean generated = ModelUtils.generateServerConfig(model);

    assertThat(generated).isTrue();
    assertThat(model.getBaseUrl()).startsWith("http://127.0.0.1:").endsWith("/v1");
    assertThat(model.getServerCommand()).isEqualTo("/opt/homebrew/bin/llama-server");
    assertThat(model.getServerArgs()).contains("-m", "/models/qwen.gguf", "--host", "127.0.0.1");
    assertThat(model.getServerArgs()).contains("--port");
  }

  @Test
  void shouldNotGenerateServerConfigWhenProviderNotOpenAiCompatible() {
    final ModelConfig model = new ModelConfig();
    model.setType("OLLAMA");
    model.setModel("qwen");

    final boolean generated = ModelUtils.generateServerConfig(model);

    assertThat(generated).isFalse();
    assertThat(model.getBaseUrl()).isNull();
  }

  @Test
  void shouldUpdatePortValueWhenServerArgsContainPortFlag() {
    final List<String> args = new ArrayList<>(List.of("--host", "127.0.0.1", "--port", "0"));

    ModelUtils.updatePortInServerArgs(args, 19001);

    assertThat(args).containsExactly("--host", "127.0.0.1", "--port", "19001");
  }

  @Test
  void shouldBuildModelsEndpointWhenBaseUrlContainsVersionPath() {
    final URI endpoint = ModelUtils.buildModelsEndpoint("http://127.0.0.1:19000/v1/");

    assertThat(endpoint).hasToString("http://127.0.0.1:19000/v1/models");
  }

  @Test
  void shouldResolveAddressWhenBaseUrlValid() {
    final ModelUtils.ServerAddress address = ModelUtils.resolveAddress("http://localhost:8080/v1");

    assertThat(address).isNotNull();
    assertThat(address.host()).isEqualTo("localhost");
    assertThat(address.port()).isEqualTo(8080);
  }
}
