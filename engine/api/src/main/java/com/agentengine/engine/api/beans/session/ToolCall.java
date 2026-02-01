package com.agentengine.engine.api.beans.session;

import java.util.Map;
import java.util.Objects;

public record ToolCall(String id, String name, Map<String, Object> args) {
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ToolCall toolCall = (ToolCall) o;
        return Objects.equals(id, toolCall.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
