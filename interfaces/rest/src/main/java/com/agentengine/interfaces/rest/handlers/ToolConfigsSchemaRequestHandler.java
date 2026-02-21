package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.beans.ToolEntity;
import com.agentengine.engine.api.services.ToolService;
import com.agentengine.interfaces.rest.requests.SchemaLookupRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collections;

/** Handler for tool configuration schemas. */
@Singleton
public class ToolConfigsSchemaRequestHandler implements SchemaRequestHandler {

  private final ToolService toolService;

  @Inject
  public ToolConfigsSchemaRequestHandler(ToolService toolService) {
    this.toolService = toolService;
  }

  @Override
  public String getAssetType() {
    return "tool_configs";
  }

  @Override
  public Object handle(SchemaLookupRequest request) {
    ToolEntity tool = toolService.getToolById(request.agentId(), request.assetId());
    if (tool != null && tool.getConfigsSchema() != null) {
      return tool.getConfigsSchema();
    }
    return Collections.emptyMap();
  }
}
