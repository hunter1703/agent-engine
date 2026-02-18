package com.agentengine.interfaces.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.interfaces.rest.catalog.handlers.AgentAssetHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class GenericAssetAPITest {

  @Test
  void getAssetByKeyReturnsCorrectAsset() {
    // Mock the AgentService
    AgentService agentService = mock(AgentService.class);
    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    when(agentService.getAgent("test-agent")).thenReturn(Optional.of(config));

    // Create the handler
    AgentAssetHandler handler = new AgentAssetHandler(agentService);

    // Prepare the request with keys
    AssetRequest requestWithKeys = new AssetRequest();
    requestWithKeys.setKeys(List.of("test-agent"));

    // Call the handler
    Map<String, AgentConfig> result = handler.getAssetsByIds(requestWithKeys);

    // Verify the result
    assertThat(result).isNotNull();
    assertThat(result.get("test-agent")).isNotNull();
    assertThat(result.get("test-agent").getId()).isEqualTo("test-agent");
  }
}