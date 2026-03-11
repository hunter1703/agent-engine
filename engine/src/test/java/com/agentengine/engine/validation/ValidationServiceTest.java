package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.common.validation.Validator;
import jakarta.enterprise.inject.Instance;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ValidationServiceTest {

  @Test
  void shouldThrowWhenConfigNull() {
    final ValidationService service =
        new ValidationService(createValidator(), emptyRuleValidators());

    assertThatThrownBy(() -> service.validate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Config is required");
  }

  @Test
  void shouldThrowWhenBeanAndRuleValidationsFail() {
    final Validator<SampleConfig> ruleValidator =
        new Validator<>() {
          @Override
          public Class<SampleConfig> targetType() {
            return SampleConfig.class;
          }

          @Override
          public void validate(final SampleConfig value, final ValidationCollector errors) {
            if (value.version < 1) {
              errors.add("version: must be >= 1");
            }
          }
        };

    final ValidationService service =
        new ValidationService(createValidator(), instanceWith(ruleValidator));

    final SampleConfig invalid = new SampleConfig();
    invalid.name = " ";
    invalid.version = 0;

    assertThatThrownBy(() -> service.validate(invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name")
        .hasMessageContaining("must not be blank")
        .hasMessageContaining("version: must be >= 1");
  }

  @Test
  void shouldPassWhenConfigSatisfiesAllValidations() {
    final Validator<SampleConfig> ruleValidator =
        new Validator<>() {
          @Override
          public Class<SampleConfig> targetType() {
            return SampleConfig.class;
          }

          @Override
          public void validate(final SampleConfig value, final ValidationCollector errors) {
            if (value.version < 1) {
              errors.add("version: must be >= 1");
            }
          }
        };

    final ValidationService service =
        new ValidationService(createValidator(), instanceWith(ruleValidator));

    final SampleConfig valid = new SampleConfig();
    valid.name = "agent";
    valid.version = 2;

    assertThatCode(() -> service.validate(valid)).doesNotThrowAnyException();
  }

  private static jakarta.validation.Validator createValidator() {
    return Validation.buildDefaultValidatorFactory().getValidator();
  }

  @SuppressWarnings("unchecked")
  private static Instance<Validator<?>> emptyRuleValidators() {
    final Instance<Validator<?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.empty());
    return instance;
  }

  @SuppressWarnings("unchecked")
  private static Instance<Validator<?>> instanceWith(final Validator<?> validator) {
    final Instance<Validator<?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of((Validator<?>) validator));
    return instance;
  }

  private static final class SampleConfig {
    @NotBlank String name;
    int version;
  }
}
