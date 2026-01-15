package com.localagent.engine.utils;

import com.alibaba.fastjson2.TypeReference;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import com.localagent.engine.message.ToolCall;
import com.localagent.engine.state.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class EngineUtils {

    private EngineUtils(){}

    public static int invocationsThisTurn(SessionStore sessionStore, String sessionId) {
        List<Message> messages = sessionStore.getMessages(sessionId);
        int count = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message.getRole() == Role.USER) {
                break;
            }
            if (message.getRole() == Role.ASSISTANT) {
                count++;
            }
        }
        return count;
    }

    public static Message sanitizeMessage(final Message message, final String format, final String thoughtsStartTag, final String thoughtsEndTag) {
        if (message == null) {
            return null;
        }
        if (Objects.equals("json", format)) {
            Message parsed = parseJsonPayload(message.getContent());
            if (parsed == null) {
                return new Message(message.getRole(), "", "", List.of(), List.of());
            }
            return new Message(
                message.getRole(),
                parsed.getContent(),
                parsed.getThoughts(),
                CollectionUtils.nullSafeList(parsed.getToolRequests()),
                CollectionUtils.nullSafeList(parsed.getToolCalls())
            );
        }
        final String content = contentForValidation(message.getContent(), thoughtsStartTag, thoughtsEndTag);
        return new Message(
            message.getRole(),
            content,
            message.getThoughts(),
            CollectionUtils.nullSafeList(message.getToolRequests()),
            CollectionUtils.nullSafeList(message.getToolCalls())
        );
    }

    public static String getRepairMessageIfInvalid(Message message) {
        final String content = message.getContent();
        final List<String> toolRequests = message.getToolRequests();
        final String thoughts = message.getThoughts();

        final boolean finalAnswerAndToolCallsPresent = StringUtils.isNotBlank(content) && CollectionUtils.isNotEmpty(toolRequests);
        final boolean emptyResponse = StringUtils.isBlank(content) && StringUtils.isBlank(thoughts) && CollectionUtils.isEmpty(toolRequests);
        return TemplateUtils.renderForName("repair/invalid_message.txt", Map.of("finalAnswerAndToolCallsPresent", finalAnswerAndToolCallsPresent, "emptyResponse", emptyResponse));
    }

    private static String contentForValidation(String text, String thoughtsStartTag, String thoughtsEndTag) {
        return stripThoughtBlock(text, thoughtsStartTag, thoughtsEndTag);
    }

    private static String stripThoughtBlock(String text, String thoughtsStartTag, String thoughtsEndTag) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        return text.replaceAll(STR."\{Pattern.quote(thoughtsStartTag)}.*?\{Pattern.quote(thoughtsEndTag)}", "").trim();
    }

    public static Message parseJsonPayload(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int end = cleaned.lastIndexOf("```");
            if (end > 2) {
                cleaned = cleaned.substring(3, end).trim();
                if (cleaned.startsWith("json")) {
                    cleaned = cleaned.substring(4).trim();
                }
            }
        }
        Map<String, Object> payload = null;
        try {
            payload = JsonUtils.fromJson(cleaned, new TypeReference<>() {});
        } catch (Exception ex) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    payload = JsonUtils.fromJson(cleaned.substring(start, end + 1), new TypeReference<>() {});
                } catch (Exception ignored) {
                }
            }
        }
        if (payload == null) {
            return null;
        }
        final String finalAnswer = getStringValue(payload, "finalAnswer");
        final String thoughts = getStringValue(payload, "thoughts");
        List<ToolCall> toolCalls = parseToolCallsFromJsonMap(payload);
        List<String> toolRequests = parseToolRequestStrings(payload.get("toolRequests"));
        if (toolRequests.isEmpty() && payload.containsKey("tool_name")) {
            toolRequests = List.of(JsonUtils.toJson(Map.of(
                "name", payload.get("tool_name"),
                "args", payload.getOrDefault("tool_args", Map.of())
            )));
        }
        String content = finalAnswer == null ? "" : finalAnswer;
        return new Message(null, content, thoughts, toolRequests, toolCalls);
    }

    private static List<ToolCall> parseToolCallsFromJsonMap(Map<String, Object> payload) {
        Object toolRequests = payload.get("toolRequests");
        if (!(toolRequests instanceof List<?> list)) {
            if (payload.containsKey("tool_name")) {
                Object nameValue = payload.get("tool_name");
                Object argsValue = payload.get("tool_args");
                Map<String, Object> args = argsValue instanceof Map<?, ?> argsMap ? (Map<String, Object>) argsMap : Map.of();
                return List.of(new ToolCall(null, nameValue == null ? "" : nameValue.toString(), args));
            }
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object nameValue = map.get("name");
            if (nameValue == null) {
                continue;
            }
            Object argsValue = map.get("args");
            Map<String, Object> args = argsValue instanceof Map<?, ?> argsMap ? (Map<String, Object>) argsMap : Map.of();
            Object idValue = map.get("id");
            calls.add(new ToolCall(idValue == null ? null : idValue.toString(), nameValue.toString(), args));
        }
        return calls;
    }

    private static List<String> parseToolRequestStrings(Object toolRequests) {
        if (!(toolRequests instanceof List<?> list)) {
            return List.of();
        }
        List<String> requests = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            if (item instanceof String value) {
                if (!value.isBlank()) {
                    requests.add(value);
                }
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                requests.add(JsonUtils.toJson(map));
                continue;
            }
            requests.add(item.toString());
        }
        return requests;
    }

    private static String getStringValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }
}
