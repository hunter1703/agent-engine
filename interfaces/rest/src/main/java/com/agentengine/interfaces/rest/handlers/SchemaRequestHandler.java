package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.SchemaLookupRequest;

/** Interface for handling schema lookup requests. */
public interface SchemaRequestHandler {

  /**
   * Returns the asset type this handler supports.
   *
   * @return the asset type
   */
  String getAssetType();

  /**
   * Resolves the schema for the given request.
   *
   * @param request
   *          the lookup request
   * @return the resolved schema (usually a Map)
   */
  Object handle(SchemaLookupRequest request);
}
