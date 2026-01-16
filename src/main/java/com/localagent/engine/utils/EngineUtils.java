package com.localagent.engine.utils;

import com.alibaba.fastjson2.TypeReference;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import com.localagent.engine.message.ToolCall;
import com.localagent.engine.state.SessionStore;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EngineUtils {

    private static final Pattern FINAL_BLOCK = Pattern.compile("FINAL:\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOOL_REQUEST_BLOCK = Pattern.compile("TOOL_REQUEST:\\s*(.*?)(?=\\n\\s*TOOL_REQUEST:|\\n\\s*FINAL:|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private EngineUtils(){}

    public static int invocationsThisTurn(final SessionStore sessionStore, final String sessionId) {
        final List<Message> messages = sessionStore.getMessages(sessionId);
        int count = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            final Message message = messages.get(i);
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
            final Message parsed = parseJsonPayload(content);
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

    public static String getRepairMessageIfInvalid(final Message message) {
        final String content = message.getContent();
        final List<String> toolRequests = message.getToolRequests();
        final String thoughts = message.getThoughts();

        final boolean finalAnswerAndToolCallsPresent = StringUtils.isNotBlank(content) && CollectionUtils.isNotEmpty(toolRequests);
        final boolean emptyResponse = StringUtils.isBlank(content) && StringUtils.isBlank(thoughts) && CollectionUtils.isEmpty(toolRequests);
        final List<ToolRequest> toolRequestInfos = parseToolRequestInfo(toolRequests);
        boolean missingToolRequestId = false;
        boolean missingToolRequestName = false;
        boolean duplicateToolRequestId = false;
        final Set<String> seenIds = new HashSet<>();
        for (ToolRequest info : toolRequestInfos) {
            if (StringUtils.isBlank(info.id())) {
                missingToolRequestId = true;
            } else if (!seenIds.add(info.id())) {
                duplicateToolRequestId = true;
            }
            if (StringUtils.isBlank(info.name())) {
                missingToolRequestName = true;
            }
        }
        return TemplateUtils.renderForName("hybrid/repair/invalid_message.txt", Map.of(
            "finalAnswerAndToolCallsPresent", finalAnswerAndToolCallsPresent,
            "emptyResponse", emptyResponse,
            "missingToolRequestId", missingToolRequestId,
            "missingToolRequestName", missingToolRequestName,
            "duplicateToolRequestId", duplicateToolRequestId
        ));
    }

    private static String getFinalAnswer(final String cleaned) {
        if (StringUtils.isBlank(cleaned)) {
            return cleaned;
        }
        final Matcher finalMatch = FINAL_BLOCK.matcher(cleaned);
        return (finalMatch.find() ? finalMatch.group(1) : cleaned).trim();
    }

    private static String stripThoughtBlock(final String text, final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag) {
        if (StringUtils.isBlank(text) || !thoughtsEnabled) {
            return text;
        }
        return text.replaceAll(STR."\{Pattern.quote(thoughtsStartTag)}.*?\{Pattern.quote(thoughtsEndTag)}", "").trim();
    }

    private static String getThoughts(final String content, final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag) {
        if (StringUtils.isBlank(content) || !thoughtsEnabled || StringUtils.isBlank(thoughtsStartTag) || StringUtils.isBlank(thoughtsEndTag)) {
            return null;
        }
        final Pattern thoughtPattern = Pattern.compile(STR."\{Pattern.quote(thoughtsStartTag)}(.*?)\{Pattern.quote(thoughtsEndTag)}", Pattern.DOTALL);
        final Matcher matcher = thoughtPattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        final String thoughts = matcher.group(1);
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

    public static List<ToolRequest> parseToolRequestInfo(final List<String> toolRequests) {
        if (CollectionUtils.isEmpty(toolRequests)) {
            return List.of();
        }
        final List<ToolRequest> infos = new ArrayList<>();
        for (String request : toolRequests) {
            String id = null;
            String name = null;
            if (StringUtils.isNotBlank(request)) {
                final Map<String, Object> jsonMap = parseToolRequestJson(request.trim());
                if (jsonMap != null) {
                    id = CollectionUtils.getStringValueFromMap(jsonMap, "id");
                    name = CollectionUtils.getStringValueFromMap(jsonMap, "name");
                    if (name == null) {
                        name = CollectionUtils.getStringValueFromMap(jsonMap, "tool");
                    }
                }
                if (StringUtils.isBlank(id) || StringUtils.isBlank(name)) {
                    final String[] lines = request.split("\\R");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("-")) {
                            trimmed = trimmed.substring(1).trim();
                        }
                        final int colonIndex = trimmed.indexOf(':');
                        if (colonIndex <= 0) {
                            continue;
                        }
                        final String key = trimmed.substring(0, colonIndex).trim().toLowerCase();
                        final String value = trimmed.substring(colonIndex + 1).trim();
                        if ("id".equals(key) && StringUtils.isBlank(id)) {
                            id = value;
                        } else if (("tool".equals(key) || "name".equals(key)) && StringUtils.isBlank(name)) {
                            name = value;
                        }
                    }
                }
            }
            infos.add(new ToolRequest(id, name, request));
        }
        return infos;
    }

    private static Map<String, Object> parseToolRequestJson(final String request) {
        if (StringUtils.isBlank(request)) {
            return null;
        }
        try {
            return JsonUtils.fromJson(request, new TypeReference<>() {});
        } catch (Exception ex) {
            final int start = request.indexOf('{');
            final int end = request.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return JsonUtils.fromJson(request.substring(start, end + 1), new TypeReference<>() {});
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static Message parseJsonPayload(final String text) {
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
            final int start = cleaned.indexOf('{');
            final int end = cleaned.lastIndexOf('}');
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
        final List<ToolCall> toolCalls = parseToolCallsFromJsonMap(payload);
        List<String> toolRequests = parseToolRequestStrings(payload.get("toolRequests"));
        if (toolRequests.isEmpty() && payload.containsKey("tool_name")) {
            toolRequests = List.of(JsonUtils.toJson(Map.of(
                "name", payload.get("tool_name"),
                "args", payload.getOrDefault("tool_args", Map.of())
            )));
        }
        final String content = finalAnswer == null ? "" : finalAnswer;
        return new Message(null, content, thoughts, toolRequests, toolCalls);
    }

    private static List<ToolCall> parseToolCallsFromJsonMap(final Map<String, Object> payload) {
        final Object toolRequests = payload.get("toolRequests");
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
            //noinspection unchecked
            final Map<String, Object> args = argsValue instanceof Map<?, ?> argsMap ? (Map<String, Object>) argsMap : Map.of();
            final Object idValue = map.get("id");
            calls.add(new ToolCall(idValue == null ? null : idValue.toString(), nameValue.toString(), args));
        }
        return calls;
    }

    private static List<String> parseToolRequestStrings(final Object toolRequests) {
        if (!(toolRequests instanceof List<?> list)) {
            return List.of();
        }
        final List<String> requests = new ArrayList<>();
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
