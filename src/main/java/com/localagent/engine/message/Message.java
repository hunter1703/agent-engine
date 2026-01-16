package com.localagent.engine.message;

import com.localagent.engine.utils.CollectionUtils;
import com.localagent.engine.utils.StringUtils;

import java.util.List;
import java.util.UUID;

public class Message {
    private final String id;
    private final Role role;
    private final String content;
    private final String thoughts;
    private final List<String> toolRequests;
    private final List<ToolCall> toolCalls;

    public Message(Role role, String content, String thoughts, List<String> toolRequests, List<ToolCall> toolCalls) {
        this.id = UUID.randomUUID().toString().replaceAll("-", "");
        this.role = role;
        this.content = content;
        this.thoughts = thoughts;
        this.toolRequests = toolRequests;
        this.toolCalls = toolCalls;
    }

    public String getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getThoughts() {
        return thoughts;
    }

    public List<String> getToolRequests() {
        return toolRequests;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content, null, null, null);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content, null, null, null);
    }

    public static Message tool(String content) {
        return new Message(Role.TOOL, content, null, null, null);
    }

    public static Message assistant(String content, String thoughts) {
        return new Message(Role.ASSISTANT, content, thoughts, null, null);
    }
}
