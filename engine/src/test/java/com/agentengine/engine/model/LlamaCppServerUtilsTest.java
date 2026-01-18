package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.beans.config.ModelConfig.Provider;
import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LlamaCppServerUtilsTest {

  @Test
  void buildModelsEndpointAppendsModelsPath() {
    URI uri = LlamaCppServerUtils.buildModelsEndpoint("http://127.0.0.1:17000/v1");

    assertThat(uri).isNotNull();
    assertThat(uri.toString()).isEqualTo("http://127.0.0.1:17000/v1/models");
  }

  @Test
  void buildModelsEndpointHandlesTrailingSlash() {
    URI uri = LlamaCppServerUtils.buildModelsEndpoint("http://127.0.0.1:17000/v1/");

    assertThat(uri).isNotNull();
    assertThat(uri.toString()).isEqualTo("http://127.0.0.1:17000/v1/models");
  }

  @Test
  void resolveAddressParsesHostAndPort() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("http://localhost:1234/v1");

    assertThat(address).isNotNull();
    assertThat(address.host()).isEqualTo("localhost");
    assertThat(address.port()).isEqualTo(1234);
  }

  @Test
  void ensureRunningWaitsForReadyEndpoint() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/models", exchange -> {
      byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    int port = server.getAddress().getPort();

    ModelConfig config = new ModelConfig();
    config.setProvider(Provider.LLAMA_CPP.name());
    config.setBaseUrl("http://127.0.0.1:" + port + "/v1");
    config.setServerCommand("");

    LlamaCppServerUtils.ensureRunning(config);

    server.stop(0);
  }
}
