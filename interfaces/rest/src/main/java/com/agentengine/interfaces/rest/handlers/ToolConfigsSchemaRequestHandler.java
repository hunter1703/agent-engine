package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.services.ToolCatalog;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.interfaces.rest.requests.SchemaLookupRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collections;

/** Handler for tool configuration schemas. */
@Singleton
public class ToolConfigsSchemaRequestHandler implements SchemaRequestHandler {

  private final ToolCatalog toolCatalog;

  @Inject
  public ToolConfigsSchemaRequestHandler(ToolCatalog toolCatalog) {
    this.toolCatalog = toolCatalog;
  }

  @Override
  public String getAssetType() {
    return "tool_configs";
  }

  @Override
  public Object handle(SchemaLookupRequest request) {
    final ToolDescriptor tool = toolCatalog.getToolByName(request.assetId());
    return tool == null ? Collections.emptyMap() : tool.configsSchema();
  }
}
