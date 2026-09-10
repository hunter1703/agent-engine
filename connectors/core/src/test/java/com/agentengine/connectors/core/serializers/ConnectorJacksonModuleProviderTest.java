package com.agentengine.connectors.core.serializers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.connectors.http.auth.HeaderAuthDecoratorSpec;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.util.common.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConnectorJacksonModuleProviderTest {

  private static ObjectMapper mapper() {
    final ObjectMapper mapper = JsonUtils.copyMapper();
    mapper.registerModule(new ConnectorJacksonModuleProvider().getModule());
    return mapper;
  }

  @Test
  void knownExecutorTypeResolvesToItsConcreteClass() throws Exception {
    final ExecutorSpec result =
        mapper()
            .readValue(
                "{\"type\":\"HTTP\",\"baseUrl\":\"https://example.com\"}", ExecutorSpec.class);

    assertThat(result).isInstanceOf(HttpExecutorSpec.class);
    assertThat(((HttpExecutorSpec) result).getBaseUrl()).isEqualTo("https://example.com");
  }

  @Test
  void knownAuthDecoratorTypeResolvesToItsConcreteClass() throws Exception {
    final AuthDecoratorSpec result =
        mapper()
            .readValue(
                "{\"type\":\"HEADER\",\"headers\":{\"Authorization\":\"secret\"}}",
                AuthDecoratorSpec.class);

    assertThat(result).isInstanceOf(HeaderAuthDecoratorSpec.class);
  }
}
