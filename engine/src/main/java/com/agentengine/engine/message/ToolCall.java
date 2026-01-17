package com.agentengine.engine.message;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> args) {
}
