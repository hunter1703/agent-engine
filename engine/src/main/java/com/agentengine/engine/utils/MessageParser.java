package com.agentengine.engine.utils;

import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.alibaba.fastjson2.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.agentengine.engine.utils.AgentUtils.parseJsonPayload;

public class MessageParser {
    private static final String FINAL_ANSWER_KEY = "finalAnswer";
    private static final String THOUGHTS_KEY = "thoughts";
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
        "\\{\\s*[\"']id[\"']\\s*:\\s*[\"']([^\"']*)[\"']\\s*,\\s*[\"']name[\"']\\s*:\\s*[\"']([^\"']*)[\"']\\s*,\\s*[\"']args[\"']\\s*:\\s*(\\{[^}]*})\\s*\\}",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private ResponseFormatType responseFormat = ResponseFormatType.TEXT;
    private boolean toolCallingAllowed = false;
    private boolean parseToolCallsFromText = false;
    private boolean thoughtsEnabled = false;
    private String thoughtsStartTag = null;
    private String thoughtsEndTag = null;

    public static MessageParser create() {
        return new MessageParser();
    }

    public MessageParser withResponseFormat(ResponseFormatType responseFormat) {
        this.responseFormat = responseFormat;
        return this;
    }

    public MessageParser toolCallingAllowed(boolean toolCallingAllowed) {
        this.toolCallingAllowed = toolCallingAllowed;
        return this;
    }

    public MessageParser parseToolCallsFromText(boolean parseToolCallsFromText) {
        this.parseToolCallsFromText = parseToolCallsFromText;
        return this;
    }


    public MessageParser areThoughtsEnabled(boolean thoughtsEnabled) {
        this.thoughtsEnabled = thoughtsEnabled;
        return this;
    }

    public MessageParser withThoughtsStartTag(String thoughtsStartTag) {
        this.thoughtsStartTag = thoughtsStartTag;
        return this;
    }

    public MessageParser withThoughtsEndTag(String thoughtsEndTag) {
        this.thoughtsEndTag = thoughtsEndTag;
        return this;
    }

    public Message parse(Message message) {
        if (message == null) {
            return null;
        }

        if (responseFormat == ResponseFormatType.JSON) {
            return parseJsonMessage(message);
        } else {
            return parseTextMessage(message);
        }
    }

    private Message parseJsonMessage(final Message message) {
        final String content = message.getContent();
        final Role role = message.getRole();
        final Message parsed = buildMessageFromJsonPayload(content);
        if (parsed == null) {
            return new Message(role, "", "", List.of());
        }
        final List<ToolCall> toolCalls = CollectionUtils.nullSafeList(toolCallingAllowed ? (parseToolCallsFromText ? parsed.getToolCalls() : message.getToolCalls()) : List.of());
        return new Message(role, parsed.getContent(), parsed.getThoughts(), toolCalls);
    }

    private Message parseTextMessage(Message message) {
        final String content = message.getContent();
        final Role role = message.getRole();
        final String processedContent = stripThoughtBlock(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
        final String thoughts = getThoughts(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
        List<ToolCall> toolCalls = message.getToolCalls();
        if (toolCallingAllowed && parseToolCallsFromText) {
            toolCalls = parseToolCalls(processedContent);
        }

        final String finalAnswer = processedContent == null ? "" : processedContent.trim();
        return new Message(role, finalAnswer, thoughts, toolCallingAllowed ? CollectionUtils.nullSafeList(toolCalls) : List.of());
    }

    private String getThoughts(String content, boolean thoughtsEnabled, String thoughtsStartTag, String thoughtsEndTag) {
        if (StringUtils.isBlank(content)
                || !thoughtsEnabled
                || StringUtils.isBlank(thoughtsStartTag)
                || StringUtils.isBlank(thoughtsEndTag)) {
            return null;
        }
        final Pattern thoughtPattern =
                Pattern.compile(
                        STR."\{Pattern.quote(thoughtsStartTag)}(.*?)\{Pattern.quote(thoughtsEndTag)}",
                        Pattern.DOTALL);
        final Matcher matcher = thoughtPattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        final String thoughts = matcher.group(1);
        return thoughts == null ? null : thoughts.trim();
    }

    private Message buildMessageFromJsonPayload(final String text) {
        final Map<String, Object> payload = parseJsonPayload(text);
        if (payload == null) {
            return null;
        }
        final String finalAnswer = CollectionUtils.getStringValueFromMap(payload, FINAL_ANSWER_KEY);
        final String thoughts = thoughtsEnabled ? CollectionUtils.getStringValueFromMap(payload, THOUGHTS_KEY) : null;
        final List<ToolCall> toolCalls = toolCallingAllowed && parseToolCallsFromText ? parseToolCallsFromJsonMap(payload) : List.of();
        return new Message(null, finalAnswer, thoughts, toolCalls);
    }

    private List<ToolCall> parseToolCallsFromJsonMap(final Map<String, Object> payload) {
        final Object toolCallsValue = payload.get("toolCalls");
        if (!(toolCallsValue instanceof List<?> list)) {
            return List.of();
        }
        final List<ToolCall> calls = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            final Object nameValue = map.get("name");
            if (nameValue == null) {
                continue;
            }
            final Object argsValue = map.get("args");
            @SuppressWarnings("unchecked")
            final Map<String, Object> args = argsValue instanceof Map<?, ?> argsMap
                    ? (Map<String, Object>) argsMap
                    : Map.of();
            final Object idValue = map.get("id");
            calls.add(new ToolCall(idValue == null ? null : idValue.toString(), nameValue.toString(), args));
        }
        return calls;
    }

    private static List<ToolCall> parseToolCalls(final String content) {
        if (StringUtils.isBlank(content)) {
            return List.of();
        }

        final List<ToolCall> toolCalls = new ArrayList<>();
        final Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        while (matcher.find()) {
            String id = matcher.group(1);
            String name = matcher.group(2);
            String argsStr = matcher.group(3);
            // Parse the arguments JSON
            Map<String, Object> args;
            try {
                args = JsonUtils.fromJson(argsStr, new TypeReference<>() {
                });
            } catch (Exception e) {
                // If parsing fails, continue with empty args
                args = Map.of();
            }

            toolCalls.add(new ToolCall(id, name, args));
        }

        return toolCalls.isEmpty() ? List.of() : toolCalls;
    }

    private static String stripThoughtBlock(
            final String text,
            final boolean thoughtsEnabled,
            final String thoughtsStartTag,
            final String thoughtsEndTag) {
        if (StringUtils.isBlank(text) || !thoughtsEnabled) {
            return text;
        }
        return text.replaceAll(
                        STR."\{Pattern.quote(thoughtsStartTag)}.*?\{Pattern.quote(thoughtsEndTag)}", "")
                .trim();
    }
}