package com.localagent.engine.beans;

import com.localagent.engine.message.Message;

import java.time.Instant;
import java.util.Map;

public record ToolExecution(String id, String name, String status, String output, Instant startedAt, long durationMs) {

    public Message toMessage() {
        return Message.tool(STR."""
                Tool Id : \{id}
                Tool Name : \{name}
                Tool Status : \{status}
                Tool output : \{output}
                """);
    }
}
