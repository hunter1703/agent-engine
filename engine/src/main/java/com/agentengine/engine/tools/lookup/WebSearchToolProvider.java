package com.agentengine.engine.tools.lookup;

import com.agentengine.connectors.core.ConnectorService;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.tools.ToolProvider;
import com.google.adk.tools.BaseTool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class WebSearchToolProvider implements ToolProvider {

  private final ConnectorService connectorService;

  @Inject
  public WebSearchToolProvider(final ConnectorService connectorService) {
    this.connectorService = connectorService;
  }

  @Override
  public ToolDescriptor descriptor() {
    return WebSearchTool.DESCRIPTOR;
  }

  @Override
  public BaseTool create(final Map<String, Object> toolConfig) {
    return new WebSearchTool(connectorService);
  }
}
