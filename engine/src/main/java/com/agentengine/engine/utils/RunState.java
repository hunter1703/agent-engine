package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.utils.TransitionResult;
import com.agentengine.engine.tools.planning.beans.Plan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RunState {
    private Phase phase;
    private transient RunStateMachine stateMachine;
    private boolean thinkingOpen;
    private String lastThoughtText;
    private Plan plan;
    private String lastToolCall;
    private final List<Violation> violations = new ArrayList<>();

    public RunState() {
        this.phase = Phase.REASONING;
    }

    public Phase phase() {
        return phase;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(final Phase phase) {
        this.phase = phase;
        this.stateMachine = new RunStateMachine(phase);
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

    public TransitionResult<Violation> transitionPhase(final Phase nextPhase) {
        if (nextPhase == null) {
            return TransitionResult.failure(null);
        }
        final RunStateMachine machine = machine();
        final TransitionResult<Violation> result = machine.next(nextPhase);
        if (!result.success() && result.value() != null) {
            addViolation(result.value());
            return result;
        }
        if (result.success()) {
            this.phase = machine.getState();
        }
        return result;
    }

    public boolean transitionPhaseIf(final Phase expected, final Phase nextPhase) {
        if (expected == null || phase != expected) {
            return false;
        }
        final RunStateMachine machine = machine();
        final TransitionResult<Violation> result = machine.tryFire(expected, nextPhase);
        if (!result.success() && result.value() != null) {
            addViolation(result.value());
            return false;
        }
        if (result.success()) {
            this.phase = machine.getState();
        }
        return result.success();
    }

    private RunStateMachine machine() {
        if (stateMachine == null || stateMachine.getState() != phase) {
            stateMachine = new RunStateMachine(phase);
        }
        return stateMachine;
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

    public enum Phase {
        UNKNOWN,
        REASONING,
        READY_FOR_FINAL_ANSWER,
        FINAL_ANSWER_REQUESTED,
        FINAL_ANSWER_DELIVERED,
        FINISHED;

        public static Phase valueOfOrDefault(final String value) {
            if (StringUtils.isBlank(value)) {
                return UNKNOWN;
            }
            try {
                return Phase.valueOf(value);
            } catch (IllegalArgumentException ex) {
                return UNKNOWN;
            }
        }
    }

}
