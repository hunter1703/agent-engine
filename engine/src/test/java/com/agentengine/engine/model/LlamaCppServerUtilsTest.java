package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class LlamaCppServerUtilsTest {

  @Test
  void ensureRunningWaitsForReadyWhenReachable() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/models", exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    server.start();
    try {
      ModelConfig config = new ModelConfig();
      config.setType(ModelConfig.Provider.LLAMA_CPP.name());
      config.setBaseUrl("http://localhost:" + server.getAddress().getPort());
      config.setModel("llama");

      LlamaCppServerUtils.ensureRunning(config);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void resolveAddressDefaultsPort() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("http://localhost");

    assertThat(address).isNotNull();
    assertThat(address.port()).isEqualTo(80);
  }

  @Test
  void resolveAddressDefaultsHttpsPort() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("https://example.com");

    assertThat(address).isNotNull();
    assertThat(address.port()).isEqualTo(443);
  }

  @Test
  void resolveAddressRejectsInvalidUrl() {
    assertThat(LlamaCppServerUtils.resolveAddress("http://bad host")).isNull();
  }

  @Test
  void buildModelsEndpointNormalizesPath() {
    URI endpoint = LlamaCppServerUtils.buildModelsEndpoint("http://localhost:8080/api/");

    assertThat(endpoint).isNotNull();
    assertThat(endpoint.getPath()).isEqualTo("/api/models");
  }

  @Test
  void buildModelsEndpointDefaultsToV1() {
    URI endpoint = LlamaCppServerUtils.buildModelsEndpoint("http://localhost:8080");

    assertThat(endpoint).isNotNull();
    assertThat(endpoint.getPath()).isEqualTo("/v1/models");
  }
}
