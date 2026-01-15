package com.localagent.engine.message;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record ToolCall(String id, String name, Map<String, Object> args) {
}
