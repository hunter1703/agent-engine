package com.agentengine.engine.validation;

public interface ConfigRuleValidator<T> {

  Class<T> targetType();

  default int order() {
    return 0;
  }

  void validate(T value, ValidationCollector errors);
}
