package com.localagent.engine;

import com.localagent.engine.message.ToolCall;
import com.localagent.engine.beans.ToolExecution;
import java.util.List;

public interface AgentListener {
    default void onToolPlan(final String sessionId, final List<ToolCall> toolCalls) {
    }

    default void onToolExecution(final String sessionId, final ToolExecution toolExecution) {
    }

    default void onReasoningStart(final String sessionId) {
    }

    default void onReasoningEnd(final String sessionId) {
    }

    default void onToolRepair(final String sessionId) {
    }
}
