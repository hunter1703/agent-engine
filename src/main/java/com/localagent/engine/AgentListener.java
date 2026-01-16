package com.localagent.engine;

import com.localagent.engine.message.ToolCall;
import com.localagent.engine.beans.ToolExecution;
import java.util.List;

public interface AgentListener {
    default void onToolPlan(String sessionId, List<ToolCall> toolCalls) {
    }

    default void onToolExecution(String sessionId, ToolExecution toolExecution) {
    }

    default void onReasoningStart(String sessionId) {
    }

    default void onReasoningEnd(String sessionId) {
    }

    default void onToolRepair(String sessionId) {
    }
}
