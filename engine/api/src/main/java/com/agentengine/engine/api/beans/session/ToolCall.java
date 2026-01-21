package com.agentengine.engine.api.beans.session;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> args) {
}
