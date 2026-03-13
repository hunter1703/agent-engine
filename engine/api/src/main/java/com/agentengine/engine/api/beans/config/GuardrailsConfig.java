package com.agentengine.engine.api.beans.config;

import com.agentengine.util.common.builder.annotations.UiBoolean;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiSelect;
import java.util.ArrayList;
import java.util.List;

public class GuardrailsConfig {
  private static final String DEFAULT_EXECUTION_MODE = GuardrailExecutionMode.SYNC.name();
  private static final String DEFAULT_ON_ERROR = GuardrailErrorMode.FAIL_OPEN.name();

  @UiField(label = "Enabled", order = 10)
  @UiBoolean
  private boolean enabled = true;

  @UiField(label = "Execution Mode", order = 20)
  @UiSelect(enumType = GuardrailExecutionMode.class)
  private String executionMode = DEFAULT_EXECUTION_MODE;

  @UiField(label = "Default On Error", order = 30)
  @UiSelect(enumType = GuardrailErrorMode.class)
  private String defaultOnError = DEFAULT_ON_ERROR;

  @UiField(label = "Rules", order = 40)
  private List<GuardrailRule> rules = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getExecutionMode() {
    return executionMode;
  }

  public void setExecutionMode(final String executionMode) {
    this.executionMode = executionMode == null || executionMode.isBlank() ? DEFAULT_EXECUTION_MODE : executionMode;
  }

  public GuardrailExecutionMode executionModeEnum() {
    return GuardrailExecutionMode.valueOfOrDefault(executionMode);
  }

  public String getDefaultOnError() {
    return defaultOnError;
  }

  public void setDefaultOnError(final String defaultOnError) {
    this.defaultOnError = defaultOnError == null || defaultOnError.isBlank() ? DEFAULT_ON_ERROR : defaultOnError;
  }

  public GuardrailErrorMode defaultOnErrorEnum() {
    return GuardrailErrorMode.valueOfOrDefault(defaultOnError);
  }

  public List<GuardrailRule> getRules() {
    return rules;
  }

  public void setRules(final List<GuardrailRule> rules) {
    this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
  }
}
