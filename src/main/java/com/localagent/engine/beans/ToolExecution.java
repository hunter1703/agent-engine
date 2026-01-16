package com.localagent.engine.beans;

import com.localagent.engine.message.Message;
import com.localagent.engine.message.ToolCall;

import javax.tools.Tool;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class ToolExecution {
    private String id;
    private final ToolCall toolCall;
    private final String status;
    private final String output;
    private final Instant startedAt;
    private final long durationMs;

    public ToolExecution(ToolCall toolCall, String status, String output, Instant startedAt, long durationMs) {
        this.toolCall = toolCall;
        this.status = status;
        this.output = output;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public String getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }


    public Message toMessage() {
        return Message.tool(STR."""
                Tool Id : \{id}
                Tool call : \{toolCall.name()}
                Tool Status : \{status}
                Tool output : \{output}
                """);
    }

}
