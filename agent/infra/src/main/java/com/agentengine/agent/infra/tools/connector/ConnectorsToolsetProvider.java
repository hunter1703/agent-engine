package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.tools.ToolsetProvider;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.ReadonlyContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Exposes a configurable subset of connectors (e.g. Reddit, X) as tools - one {@link ConnectorTool}
 * instance per connector, each with its own name, description, and schema.
 */
@Singleton
public final class ConnectorsToolsetProvider implements ToolsetProvider {
  private static final String CONNECTORS_CONFIG_KEY = "connectors";
  private static final ToolDescriptor TOOLSET_DESCRIPTOR =
      new ToolDescriptor(
          "connectors",
          "Exposes configured connectors as individual tools, one per connector.",
          Map.of());

  private final ConnectorService connectorService;
  private final ConnectorCacheService connectorCacheService;

  @Inject
  public ConnectorsToolsetProvider(
      final ConnectorService connectorService, final ConnectorCacheService connectorCacheService) {
    this.connectorService = connectorService;
    this.connectorCacheService = connectorCacheService;
  }

  @Override
  public ToolDescriptor descriptor() {
    return TOOLSET_DESCRIPTOR;
  }

  @Override
  public BaseToolset create(final Map<String, Object> toolConfig) {
    final Map<String, List<String>> connectorConfigs =
        CollectionUtils.getMapFromMap(toolConfig, CONNECTORS_CONFIG_KEY);
    return new ConnectorsToolset(connectorService, connectorCacheService, connectorConfigs);
  }

  private static final class ConnectorsToolset implements BaseToolset {
    private final ConnectorService connectorService;
    private final ConnectorCacheService connectorCacheService;
    private final Map<String, List<String>> connectorConfigs;

    private ConnectorsToolset(
        ConnectorService connectorService,
        ConnectorCacheService connectorCacheService,
        Map<String, List<String>> connectorConfigs) {
      this.connectorService = connectorService;
      this.connectorCacheService = connectorCacheService;
      this.connectorConfigs = connectorConfigs;
    }

    @Override
    public Flowable<BaseTool> getTools(final ReadonlyContext context) {
      Flowable<BaseTool> tools = Flowable.empty();

      for (final Entry<String, List<String>> entry : connectorConfigs.entrySet()) {
        final String appName = entry.getKey();
        for (final String connectorName : entry.getValue()) {
          tools =
              tools.concatWith(
                  Flowable.defer(
                      () -> {
                        final ConnectorTool tool =
                            new ConnectorTool(
                                appName, connectorName, connectorService, connectorCacheService);
                        return Flowable.just(tool);
                      }));
        }
      }
      return tools;
    }

    @Override
    public void close() {
      // No resources to release.
    }
  }
}
