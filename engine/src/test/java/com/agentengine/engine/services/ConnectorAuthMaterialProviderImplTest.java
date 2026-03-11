package com.agentengine.engine.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.agentengine.connectors.core.runtime.Connection;
import com.agentengine.engine.repository.ConnectionRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorAuthMaterialProviderImplTest {

  @Mock private ConnectionRepository connectionRepository;

  @InjectMocks private ConnectorAuthMaterialProviderImpl provider;

  @Test
  void shouldReturnEmptyMapWhenConnectionNotFound() {
    when(connectionRepository.findByAppName("brave")).thenReturn(null);

    final Map<String, Object> result = provider.resolve("brave");

    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnInputsWhenConnectionFound() {
    final Connection connection =
        new Connection(
            "brave", Map.of("token", "token-value", "apiKey", "api-key-value", "region", "US"));
    when(connectionRepository.findByAppName("brave")).thenReturn(connection);

    final Map<String, Object> result = provider.resolve("brave");

    assertThat(result)
        .containsEntry("token", "token-value")
        .containsEntry("apiKey", "api-key-value")
        .containsEntry("region", "US");
  }
}
