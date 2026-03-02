package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RunState {
    private boolean thinkingOpen;
    private String lastThoughtText;
    private Plan plan;
    private String lastToolCall;
    private final List<Violation> violations = new ArrayList<>();

    public RunState() {
    }

    public boolean thinkingOpen() {
        return thinkingOpen;
    }

    public boolean isThinkingOpen() {
        return thinkingOpen;
    }

    public void setThinkingOpen(final boolean thinkingOpen) {
        this.thinkingOpen = thinkingOpen;
    }

    public String lastThoughtText() {
        return lastThoughtText;
    }

    public String getLastThoughtText() {
        return lastThoughtText;
    }

    public void setLastThoughtText(final String text) {
        this.lastThoughtText = text;
    }

    public Plan plan() {
        return plan;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(final Plan plan) {
        this.plan = plan;
    }

    public String lastToolCall() {
        return lastToolCall;
    }

    public String getLastToolCall() {
        return lastToolCall;
    }

    public void setLastToolCall(final String lastToolCall) {
        this.lastToolCall = lastToolCall;
    }

    public List<Violation> violations() {
        return List.copyOf(violations);
    }

    public List<Violation> getViolations() {
        return List.copyOf(violations);
    }

    public void setViolations(final List<Violation> violations) {
        this.violations.clear();
        addViolations(violations);
    }

    public void markThinkingOpen() {
        this.thinkingOpen = true;
    }

    public void markThinkingClosed() {
        this.thinkingOpen = false;
        this.lastThoughtText = null;
    }

    public void updateLastThoughtText(final String text) {
        if (StringUtils.isNotBlank(text)) {
            this.lastThoughtText = text;
        }
    }

    public void updatePlan(final Plan plan) {
        this.plan = plan;
    }

    public void updateLastToolCall(final String toolCallSummary) {
        if (StringUtils.isBlank(toolCallSummary)) {
            this.lastToolCall = null;
        } else {
            this.lastToolCall = toolCallSummary;
        }
    }

    public void addViolation(final Violation violation) {
        if (violation == null) {
            return;
        }
        violations.removeIf(existing -> Objects.equals(existing.getCode(), violation.getCode()));
        violations.add(violation);
    }

    public void addViolations(final List<Violation> violations) {
        if (violations == null) {
            return;
        }
        for (final Violation violation : violations) {
            addViolation(violation);
        }
    }

    public void clearViolations() {
        violations.clear();
    }
}
