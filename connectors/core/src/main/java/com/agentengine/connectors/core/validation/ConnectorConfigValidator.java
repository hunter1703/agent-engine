package com.agentengine.connectors.core.validation;

import com.agentengine.connectors.core.config.ConnectorDefinition;

public interface ConnectorConfigValidator {

  ConfigValidationResult validate(ConnectorDefinition definition);

  default void validateOrThrow(final ConnectorDefinition definition) {
    final ConfigValidationResult result = validate(definition);
    if (result.hasErrors()) {
      throw new ConnectorConfigValidationException(result.issues());
    }
  }
}
