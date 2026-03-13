package com.agentengine.util.common.validation;

import com.agentengine.util.common.StringUtils;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import java.util.Comparator;
import java.util.List;

@Singleton
public class ValidationService {
  private final jakarta.validation.Validator validator;
  private final List<Validator<?>> ruleValidators;

  @Inject
  public ValidationService(final jakarta.validation.Validator validator, final @Any Instance<Validator<?>> allRuleValidators) {
    this.validator = validator;
    this.ruleValidators = allRuleValidators.stream().sorted(Comparator.comparingInt(Validator::order)).toList();
  }

  public <T> void validate(final T value) {
    if (value == null) {
      throw new IllegalArgumentException("Config is required.");
    }
    final ValidationCollector errors = new ValidationCollector();
    collectBeanValidationErrors(value, errors);
    applyRuleValidators(value, errors);
    if (!errors.hasErrors()) {
      return;
    }
    throw new IllegalArgumentException("Config validation failed: " + String.join("; ", errors.errors()));
  }

  private <T> void collectBeanValidationErrors(final T value, final ValidationCollector errors) {
    for (final ConstraintViolation<T> violation : validator.validate(value)) {
      final String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
      final String message = violation.getMessage();
      if (StringUtils.isBlank(path)) {
        errors.add(message);
      } else {
        errors.add(path + ": " + message);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T> void applyRuleValidators(final T value, final ValidationCollector errors) {
    for (final Validator<?> candidate : ruleValidators) {
      if (candidate == null || !candidate.targetType().isInstance(value)) {
        continue;
      }
      final Validator<T> validator = (Validator<T>) candidate;
      validator.validate(value, errors);
    }
  }
}
