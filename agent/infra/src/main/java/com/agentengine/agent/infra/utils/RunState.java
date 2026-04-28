package com.agentengine.agent.infra.utils;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.BaseAgentState;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.State;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionCall;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RunState extends BaseAgentState {
    public static final String PLAN_KEY = State.APP_PREFIX + "run.plan";

    private Plan plan;
    private final List<ToolCallSignature> lastToolCalls = new ArrayList<>();
    private boolean continuationRequested;
    private int offTopicRetries;
    private int turnsUsed;
    private final List<Violation> violations = new ArrayList<>();

    public RunState() {}

    /**
     * Reconstructs persisted RunState fields from the session event log.
     *
     * <p>Persisted fields (plan, lastToolCalls) are rebuilt from event history. Transient fields
     * (violations, offTopicRetries, turnsUsed, continuationRequested) always start fresh, scoped to
     * the current invocation only.
     */
    public static RunState buildFrom(final List<Event> events) {
        if (CollectionUtils.isEmpty(events)) {
            return new RunState();
        }
        final RunState state = new RunState();
        state.updatePlan(readLatestPlanSnapshot(events));
        state.updateLastToolCalls(readLastToolCalls(events));
        return state;
    }

    @SuppressWarnings("unchecked")
    private static Plan readLatestPlanSnapshot(final List<Event> events) {
        final Object value = EventUtils.latestDeltaValue(events, PLAN_KEY);
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        return JsonUtils.fromMap((Map<String, Object>) value, Plan.class);
    }

    private static List<ToolCallSignature> readLastToolCalls(final List<Event> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            final List<FunctionCall> calls = events.get(i).functionCalls();
            if (!calls.isEmpty()) {
                return calls.stream()
                        .map(functionCall -> new ToolCallSignature(
                                functionCall.name().orElse(null),
                                functionCall.args().orElse(Map.of())))
                        .toList();
            }
        }
        return List.of();
    }

    public Plan plan() {
        return plan;
    }

    public boolean hasActivePlan() {
        return plan != null && !plan.getStatus().isTerminal();
    }

    public List<ToolCallSignature> lastToolCalls() {
        return List.copyOf(lastToolCalls);
    }

    public List<Violation> violations() {
        return List.copyOf(violations);
    }

    public void updatePlan(final Plan plan) {
        this.plan = plan;
    }

    public void updatePlan(final Plan plan, final ToolContext toolContext) {
        this.plan = plan;
        if (toolContext == null || plan == null) {
            return;
        }
        final ConcurrentMap<String, Object> delta = new ConcurrentHashMap<>();
        delta.put(PLAN_KEY, JsonUtils.toMap(plan));
        toolContext.setActions(EventActions.builder().stateDelta(delta).build());
    }

    public void updateLastToolCalls(final List<ToolCallSignature> toolCalls) {
        lastToolCalls.clear();
        if (CollectionUtils.isNotEmpty(toolCalls)) {
            lastToolCalls.addAll(toolCalls);
        }
    }

    public void addViolation(final Violation violation) {
        if (violation == null) {
            return;
        }
        violations.removeIf(existing -> Objects.equals(existing.code(), violation.code()));
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

    public int incrementOffTopicRetries() {
        offTopicRetries += 1;
        return offTopicRetries;
    }

    public void resetOffTopicRetries() {
        offTopicRetries = 0;
    }

    public boolean consumeTurn(final int limit) {
        return ++turnsUsed <= limit;
    }

    public void requestContinuation(Violation violation) {
        addViolation(violation);
        this.continuationRequested = true;
    }

    public boolean consumeContinuation() {
        final boolean was = continuationRequested;
        continuationRequested = false;
        return was;
    }

    public record ToolCallSignature(String name, Map<String, Object> args) {
        public ToolCallSignature {
            name = StringUtils.isBlank(name) ? "unknown" : name;
            args = args == null ? Map.of() : Map.copyOf(new HashMap<>(args));
        }
    }
}
