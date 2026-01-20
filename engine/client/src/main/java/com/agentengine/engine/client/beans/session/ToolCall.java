package com.agentengine.engine.client.beans.session;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> args) {
}
