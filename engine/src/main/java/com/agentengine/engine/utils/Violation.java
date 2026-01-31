/*
 * Copyright 2025 Google LLC
 */

package com.agentengine.engine.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Violation implements Serializable {

    private final String code;
    private final Map<String, Object> details;
    private final List<Violation> subViolations;
    private final String message;

    private Violation(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.details = Map.copyOf(builder.details);
        this.subViolations = List.copyOf(builder.subViolations);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public List<Violation> getSubViolations() {
        return subViolations;
    }

    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key, T defaultValue) {
        return (T) details.getOrDefault(key, defaultValue);
    }

    /**
     * Get a detail value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key) {
        return (T) details.get(key);
    }

    public int getTotalViolationCount() {
        return 1 + subViolations.stream()
                .mapToInt(Violation::getTotalViolationCount)
                .sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Violation{code='").append(code).append("'");
        if (message != null) {
            sb.append(", message='").append(message).append("'");
        }
        if (!details.isEmpty()) {
            sb.append(", details=").append(details);
        }
        if (!subViolations.isEmpty()) {
            sb.append(", subViolations=").append(subViolations.size());
        }
        sb.append("}");
        return sb.toString();
    }

    public static Builder builder(String code) {
        return new Builder(code);
    }

    public static class Builder {
        private final String code;
        private String message;
        private final Map<String, Object> details = new HashMap<>();
        private final List<Violation> subViolations = new ArrayList<>();

        private Builder(String code) {
            if (code == null || code.trim().isEmpty()) {
                throw new IllegalArgumentException("Violation code cannot be null or empty");
            }
            this.code = code;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details.putAll(details);
            return this;
        }

        public Builder subViolation(Violation violation) {
            this.subViolations.add(violation);
            return this;
        }

        public Builder subViolations(List<Violation> violations) {
            this.subViolations.addAll(violations);
            return this;
        }

        public Violation build() {
            return new Violation(this);
        }
    }

    public static class Codes {
        public static final String ANSWER_WITH_TOOL_CALLS = "answer_with_tool_calls";
        public static final String INVALID_TOOL_USAGE = "invalid_tool_usage";
        public static final String MISSING_REQUIRED_PARAM = "missing_required_param";
        public static final String WRONG_PARAM_TYPE = "wrong_param_type";
        public static final String FORMAT_VIOLATION = "format_violation";
    }
}