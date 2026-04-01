package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiBoolean;
import com.agentengine.util.common.builder.annotations.UiField;
import java.util.ArrayList;
import java.util.List;

public class GuardrailsConfig {
    private static final String DEFAULT_ON_ERROR = GuardrailErrorMode.FAIL_OPEN.name();

    @UiField(label = "Enabled", order = 10)
    @UiBoolean
    private boolean enabled = true;

    @UiField(label = "Default On Error", order = 20)
    private String defaultOnError = DEFAULT_ON_ERROR;

    @UiField(label = "Rules", order = 30)
    private List<GuardrailRule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
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
