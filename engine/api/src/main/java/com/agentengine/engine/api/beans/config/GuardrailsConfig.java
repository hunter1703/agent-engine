package com.agentengine.engine.api.beans.config;

import java.util.ArrayList;
import java.util.List;

public class GuardrailsConfig {
  private boolean enabled = true;
  private GuardrailExecutionMode executionMode = GuardrailExecutionMode.SYNC;
  private GuardrailErrorMode defaultOnError = GuardrailErrorMode.FAIL_OPEN;
  private List<GuardrailRuleConfig> rules = new ArrayList<>();
  private TopicControlConfig topicControl = new TopicControlConfig();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public GuardrailExecutionMode getExecutionMode() {
    return executionMode;
  }

  public void setExecutionMode(final GuardrailExecutionMode executionMode) {
    this.executionMode = executionMode;
  }

  public GuardrailErrorMode getDefaultOnError() {
    return defaultOnError;
  }

  public void setDefaultOnError(final GuardrailErrorMode defaultOnError) {
    this.defaultOnError = defaultOnError;
  }

  public List<GuardrailRuleConfig> getRules() {
    return rules;
  }

  public void setRules(final List<GuardrailRuleConfig> rules) {
    this.rules = rules;
  }

  public TopicControlConfig getTopicControl() {
    return topicControl;
  }

  public void setTopicControl(final TopicControlConfig topicControl) {
    this.topicControl = topicControl;
  }
}
