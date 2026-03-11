package com.agentengine.util.common.validation;

public interface Validator<T> {

  Class<T> targetType();

  default int order() {
    return 0;
  }

  void validate(T value, ValidationCollector errors);
}
