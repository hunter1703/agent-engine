package com.localagent.engine.state;

import java.time.Instant;
import java.util.Map;

public record ToolExecution(String id, String name, String status, String output, Instant startedAt, long durationMs) {
}
