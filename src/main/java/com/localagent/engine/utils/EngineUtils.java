package com.localagent.engine.utils;

import com.alibaba.fastjson2.TypeReference;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import com.localagent.engine.message.ToolCall;
import com.localagent.engine.state.SessionStore;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EngineUtils {

    private static final Pattern FINAL_BLOCK = Pattern.compile("FINAL:\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOOL_REQUEST_BLOCK = Pattern.compile("TOOL_REQUEST:\\s*(.*?)(?=\\n\\s*TOOL_REQUEST:|\\n\\s*FINAL:|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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

    public static Message sanitizeMessage(final Message message, final ResponseFormat format, final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag) {
        if (message == null) {
            return null;
        }
        final String content = message.getContent();
        if (format.type() == ResponseFormatType.JSON) {
            Message parsed = parseJsonPayload(content);
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
        final String cleaned = stripThoughtBlock(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
        final List<String> toolRequests = getToolRequests(cleaned);
        final String finalAnswer = toolRequests.isEmpty() ? getFinalAnswer(cleaned) : null;
        final String thoughts = getThoughts(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
        return new Message(
            message.getRole(),
            finalAnswer,
            thoughts,
            toolRequests,
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

    private static String getFinalAnswer(final String cleaned) {
        if (StringUtils.isBlank(cleaned)) {
            return cleaned;
        }
        Matcher finalMatch = FINAL_BLOCK.matcher(cleaned);
        return (finalMatch.find() ? finalMatch.group(1) : cleaned).trim();
    }

    private static String stripThoughtBlock(String text, boolean thoughtsEnabled, String thoughtsStartTag, String thoughtsEndTag) {
        if (StringUtils.isBlank(text) || !thoughtsEnabled) {
            return text;
        }
        return text.replaceAll(STR."\{Pattern.quote(thoughtsStartTag)}.*?\{Pattern.quote(thoughtsEndTag)}", "").trim();
    }

    private static String getThoughts(final String content, final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag) {
        if (StringUtils.isBlank(content) || !thoughtsEnabled || StringUtils.isBlank(thoughtsStartTag) || StringUtils.isBlank(thoughtsEndTag)) {
            return null;
        }
        Pattern thoughtPattern = Pattern.compile(STR."\{Pattern.quote(thoughtsStartTag)}(.*?)\{Pattern.quote(thoughtsEndTag)}", Pattern.DOTALL);
        Matcher matcher = thoughtPattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String thoughts = matcher.group(1);
        return thoughts == null ? null : thoughts.trim();
    }

    private static List<String> getToolRequests(final String cleaned) {
        if (StringUtils.isBlank(cleaned)) {
            return List.of();
        }
        final Matcher matcher = TOOL_REQUEST_BLOCK.matcher(cleaned);
        final List<String> toolRequests = new ArrayList<>();
        while (matcher.find()) {
            final String toolRequest = matcher.group(1) == null ? null : matcher.group(1).trim();
            if (StringUtils.isNotBlank(toolRequest)) {
                toolRequests.add(toolRequest);
            }
        }
        return toolRequests;
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
        final String finalAnswer = CollectionUtils.getStringValueFromMap(payload, "finalAnswer");
        final String thoughts = CollectionUtils.getStringValueFromMap(payload, "thoughts");
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
                //noinspection unchecked
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
            //noinspection unchecked
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
}
