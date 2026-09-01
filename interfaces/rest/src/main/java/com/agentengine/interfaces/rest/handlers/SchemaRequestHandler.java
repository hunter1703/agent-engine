package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.SchemaLookupRequest;

public interface SchemaRequestHandler {

  String getAssetType();

  /** The returned schema is usually a Map. */
  Object handle(SchemaLookupRequest request);
}
