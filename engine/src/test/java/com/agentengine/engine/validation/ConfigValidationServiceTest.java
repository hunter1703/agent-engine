package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigValidationServiceTest {

  @Test
  void shouldThrowWhenConfigNull() {
    final ConfigValidationService service =
        new ConfigValidationService(createValidator(), emptyRuleValidators());

    assertThatThrownBy(() -> service.validate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Config is required");
  }

  @Test
  void shouldThrowWhenBeanAndRuleValidationsFail() {
    final ConfigRuleValidator<SampleConfig> ruleValidator =
        new ConfigRuleValidator<>() {
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

    final ConfigValidationService service =
        new ConfigValidationService(createValidator(), instanceWith(ruleValidator));

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
    final ConfigRuleValidator<SampleConfig> ruleValidator =
        new ConfigRuleValidator<>() {
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

    final ConfigValidationService service =
        new ConfigValidationService(createValidator(), instanceWith(ruleValidator));

    final SampleConfig valid = new SampleConfig();
    valid.name = "agent";
    valid.version = 2;

    assertThatCode(() -> service.validate(valid)).doesNotThrowAnyException();
  }

  private static Validator createValidator() {
    return Validation.buildDefaultValidatorFactory().getValidator();
  }

  @SuppressWarnings("unchecked")
  private static Instance<ConfigRuleValidator<?>> emptyRuleValidators() {
    final Instance<ConfigRuleValidator<?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.empty());
    return instance;
  }

  @SuppressWarnings("unchecked")
  private static Instance<ConfigRuleValidator<?>> instanceWith(final ConfigRuleValidator<?> validator) {
    final Instance<ConfigRuleValidator<?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of((ConfigRuleValidator<?>) validator));
    return instance;
  }

  private static final class SampleConfig {
    @NotBlank String name;
    int version;
  }
}
