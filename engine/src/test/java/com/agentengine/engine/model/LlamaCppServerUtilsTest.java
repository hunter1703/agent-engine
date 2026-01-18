package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
}
