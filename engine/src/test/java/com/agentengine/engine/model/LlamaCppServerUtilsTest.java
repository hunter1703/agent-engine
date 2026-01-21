package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.ModelConfig;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlamaCppServerUtilsTest {

  @Test
  void resolveAddressHandlesVariousUrls() {
    LlamaCppServerUtils.ServerAddress addr = LlamaCppServerUtils.resolveAddress("http://localhost:8080");
    assertThat(addr.host()).isEqualTo("localhost");
    assertThat(addr.port()).isEqualTo(8080);

    addr = LlamaCppServerUtils.resolveAddress("https://example.com");
    assertThat(addr.host()).isEqualTo("example.com");
    assertThat(addr.port()).isEqualTo(443);

    addr = LlamaCppServerUtils.resolveAddress("http://127.0.0.1");
    assertThat(addr.host()).isEqualTo("127.0.0.1");
    assertThat(addr.port()).isEqualTo(80);

    assertThat(LlamaCppServerUtils.resolveAddress(null)).isNull();
    assertThat(LlamaCppServerUtils.resolveAddress("invalid-url")).isNull();
  }

  @Test
  void buildModelsEndpointNormalizesPath() {
    URI uri = LlamaCppServerUtils.buildModelsEndpoint("http://localhost:8080");
    assertThat(uri.toString()).isEqualTo("http://localhost:8080/v1/models");

    uri = LlamaCppServerUtils.buildModelsEndpoint("http://localhost:8080/");
    assertThat(uri.toString()).isEqualTo("http://localhost:8080/v1/models");

    uri = LlamaCppServerUtils.buildModelsEndpoint("http://localhost:8080/api/v2");
    assertThat(uri.toString()).isEqualTo("http://localhost:8080/api/v2/models");

    assertThat(LlamaCppServerUtils.buildModelsEndpoint(null)).isNull();
  }

  @Test
  void ensureRunningReturnsEarlyOnInvalidConfig() {
    // Should not throw
    LlamaCppServerUtils.ensureRunning(null);

    ModelConfig config = new ModelConfig();
    config.setProvider("OLLAMA");
    LlamaCppServerUtils.ensureRunning(config);
  }

  @Test
  void testManagedServerRecord() {
    LlamaCppServerUtils.ManagedServer server = new LlamaCppServerUtils.ManagedServer("url", null, List.of("cmd"));
    assertThat(server.baseUrl()).isEqualTo("url");
    assertThat(server.process()).isNull();
    assertThat(server.command()).contains("cmd");
  }

  @Test
  void ensureRunningReturnsEarlyOnMissingCommand() {
    ModelConfig config = new ModelConfig();
    config.setProvider("LLAMA_CPP");
    config.setBaseUrl(null);
    LlamaCppServerUtils.ensureRunning(config);
  }
}
