package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.tools.ToolsetProvider;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.util.agents.beans.tools.ConnectorToolConfigsList;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.builder.BuilderDefinition;
import com.agentengine.util.agents.builder.BuilderDefinitionUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.adk.agents.ReadonlyContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.Map.Entry;

/**
 * Exposes a configurable subset of connectors (e.g. Reddit, X) as tools - one {@link ConnectorTool}
 * instance per connector, each with its own name, description, and schema.
 */
@Singleton
public final class ConnectorsToolsetProvider implements ToolsetProvider {
  private static final ToolDescriptor TOOLSET_DESCRIPTOR = buildDescriptor();

  private final ConnectionService connectionService;
  private final ConnectorCacheService connectorCacheService;
  private final ConnectorToolDeclarationCache connectorToolDeclarationCache;

  @Inject
  public ConnectorsToolsetProvider(
      final ConnectionService connectionService,
      final ConnectorCacheService connectorCacheService,
      final ConnectorToolDeclarationCache connectorToolDeclarationCache) {
    this.connectionService = connectionService;
    this.connectorCacheService = connectorCacheService;
    this.connectorToolDeclarationCache = connectorToolDeclarationCache;
  }

  @Override
  public ToolDescriptor descriptor() {
    return TOOLSET_DESCRIPTOR;
  }

  @Override
  public BaseToolset create(final Map<String, Object> toolConfig) {
    final Map<String, List<String>> connectorConfigs = buildConnectorConfigs(toolConfig);
    return new ConnectorsToolset(
        connectionService, connectorCacheService, connectorToolDeclarationCache, connectorConfigs);
  }

  private static Map<String, List<String>> buildConnectorConfigs(
      final Map<String, Object> toolConfig) {
    final List<Map<String, Object>> configMapsList =
        CollectionUtils.getListFromMap(toolConfig, "connectors");
    final Map<String, List<String>> result = new HashMap<>();
    for (final Map<String, Object> config : CollectionUtils.nullSafeList(configMapsList)) {
      final String app = CollectionUtils.getStringValueFromMap(config, "app");
      final List<String> connectors = CollectionUtils.getListFromMap(config, "connectors");
      if (CollectionUtils.isNotEmpty(connectors)) {
        result.put(app, connectors);
      }
    }
    return result;
  }

  private static ToolDescriptor buildDescriptor() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(ConnectorToolConfigsList.class);
    return new ToolDescriptor(
        "connectors",
        "Exposes configured connectors as individual tools, one per connector.",
        definition.schema(),
        definition.layout());
  }

  private static final class ConnectorsToolset implements BaseToolset {
    private final ConnectionService connectionService;
    private final ConnectorCacheService connectorCacheService;
    private final ConnectorToolDeclarationCache connectorToolDeclarationCache;
    private final Map<String, List<String>> connectorConfigs;

    private ConnectorsToolset(
        ConnectionService connectionService,
        ConnectorCacheService connectorCacheService,
        ConnectorToolDeclarationCache connectorToolDeclarationCache,
        Map<String, List<String>> connectorConfigs) {
      this.connectionService = connectionService;
      this.connectorCacheService = connectorCacheService;
      this.connectorToolDeclarationCache = connectorToolDeclarationCache;
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
                                appName,
                                connectorName,
                                connectionService,
                                connectorCacheService,
                                connectorToolDeclarationCache);
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
