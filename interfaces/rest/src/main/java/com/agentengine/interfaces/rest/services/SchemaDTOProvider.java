package com.agentengine.interfaces.rest.services;

import java.util.Map;

/**
 * Registers the asset types a module's schemas can be generated for. Every bean implementing this
 * is picked up by {@link BuilderDefinitionService}, so a module other than this one can expose its
 * own beans through {@code GET /v1/schemas/{assetType}} without changing this module.
 */
public interface SchemaDTOProvider {

  /** Asset type name to the bean class its schema is generated from. */
  Map<String, Class<?>> assetClasses();
}
