package com.agentengine.interfaces.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.utils.Page;
import com.agentengine.interfaces.rest.catalog.handlers.AgentAssetHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class GenericAssetAPITest {

    @Test
    void getAssetByKeyReturnsCorrectAsset() {
        // Mock the AgentRepository
        AgentRepository configRepository = mock(AgentRepository.class);
        AgentConfig config = new AgentConfig();
        config.setAgentId("test-agent");
        when(configRepository.findById("test-agent")).thenReturn(Optional.of(config));  // Changed from getAgentConfig() to findById()

        // Create the handler
        AgentAssetHandler handler = new AgentAssetHandler(configRepository);

        // Prepare the request with keys
        AssetRequest requestWithKeys = new AssetRequest();
        requestWithKeys.setKeys(List.of("test-agent"));
        
        // Call the handler
        Map<String, AgentConfig> result = handler.getAssetsByIds(requestWithKeys);

        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.get("test-agent")).isNotNull();
        assertThat(result.get("test-agent").getAgentId()).isEqualTo("test-agent");
    }
}