package com.agentengine.interfaces.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.interfaces.rest.catalog.handlers.AgentAssetHandler;
import java.util.List;
import java.util.Map;

import com.agentengine.engine.utils.Page;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

class ResourceCatalogAPITest {

    private ResourceCatalogAPI resourceCatalogAPI;
    private AgentAssetHandler mockHandler;

    @BeforeEach
    void setUp() {
        mockHandler = mock(AgentAssetHandler.class);
        when(mockHandler.getAssetType()).thenReturn("test");

        @SuppressWarnings("unchecked")
        Instance<AssetHandler<?>> mockInstance = mock(Instance.class);
        when(mockInstance.stream()).thenAnswer(invocation -> List.of(mockHandler).stream());

        resourceCatalogAPI = new ResourceCatalogAPI(mockInstance);
    }

    @Test
    void searchResourcesReturnsCorrectResponse() {
        // Setup mock
        AssetResponse<AgentConfig> mockResponse = mock(AssetResponse.class);
        when(mockHandler.findAssets(any(AssetRequest.class))).thenReturn(mockResponse);

        // Create request
        AssetRequest request = new AssetRequest();
        request.setAssetType("test");
        request.setPage(new Page(0, 20));

        // Call the API
        AssetResponse<Object> response = (AssetResponse<Object>) resourceCatalogAPI.searchResources(request);

        // Verify
        assertThat(response).isEqualTo(mockResponse);
    }

    @Test
    void searchResourcesThrowsExceptionForMissingAssetType() {
        // Create request without asset type
        AssetRequest request = new AssetRequest();

        // Verify exception is thrown
        try {
            resourceCatalogAPI.searchResources(request);
        } catch (WebApplicationException e) {
            assertThat(e.getResponse().getStatus()).isEqualTo(400);
            assertThat(e.getMessage()).contains("Resource type is required");
        }
    }

    @Test
    void searchResourcesThrowsExceptionForUnsupportedAssetType() {
        // Create request with unsupported asset type
        AssetRequest request = new AssetRequest();
        request.setAssetType("unsupported");

        // Verify exception is thrown
        try {
            resourceCatalogAPI.searchResources(request);
        } catch (WebApplicationException e) {
            assertThat(e.getResponse().getStatus()).isEqualTo(400);
            assertThat(e.getMessage()).contains("Unsupported resource type");
        }
    }

    @Test
    void getResourceReturnsCorrectAsset() {
        // Setup mock
        AgentConfig mockAsset = mock(AgentConfig.class);
        Map<String, AgentConfig> mockResultMap = Map.of("key", mockAsset);
        when(mockHandler.getAssetsByIds(any(AssetRequest.class))).thenReturn(mockResultMap);

        // Call the API
        Object result = resourceCatalogAPI.getResource("test", "key", null);

        // Verify
        assertThat(result).isEqualTo(mockAsset);
    }

    @Test
    void getResourceThrowsExceptionForUnsupportedAssetType() {
        // Verify exception is thrown
        try {
            resourceCatalogAPI.getResource("unsupported", "key", null);
        } catch (WebApplicationException e) {
            assertThat(e.getResponse().getStatus()).isEqualTo(400);
            assertThat(e.getMessage()).contains("Unsupported resource type");
        }
    }
}