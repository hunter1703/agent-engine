package com.localagent.engine.utils;

import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.schema.JSONSchema;
import com.alibaba.fastjson2.schema.ValidateResult;
import com.localagent.engine.DefaultAgentEngine;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import com.localagent.engine.message.ToolCall;
import com.localagent.engine.state.SessionStore;

import java.util.*;
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

        if (Objects.equals("json", format)) {
            Message payload = parseJsonPayload(message.getContent());
        } else {
            final String content = contentForValidation(message.getContent(), thoughtsStartTag, thoughtsEndTag);
        }
            if (payload.payload == null) {
                return DefaultAgentEngine.ReasonerParse.invalid(response, "Invalid JSON response");
            }
            String finalAnswer = payload.finalAnswer;
            List<ToolCall> toolRequests = payload.toolRequests;
            boolean hasFinal = finalAnswer != null && !finalAnswer.isBlank();
            boolean hasToolRequest = !toolRequests.isEmpty();
            if (state.toolCompleted && state.toolFailureCount == 0 && hasToolRequest) {
                toolRequests = List.of();
                hasToolRequest = false;
                finalAnswer = null;
                hasFinal = false;
            }
            if ((hasToolRequest && hasFinal) || (!hasToolRequest && !hasFinal)) {
                return DefaultAgentEngine.ReasonerParse.invalid(response, hasToolRequest ? "Both TOOL_REQUEST and FINAL present" : "Missing TOOL_REQUEST or FINAL");
            }
            String toolRequestText = toolRequestTextFromJson(toolRequests);
            Message message = response;
            if (toolRequestText != null) {
                message = Message.assistant("TOOL_REQUEST:\n" + toolRequestText);
            } else if (finalAnswer != null) {
                message = Message.assistant("FINAL: " + finalAnswer);
            }
            return new DefaultAgentEngine.ReasonerParse(message, toolRequestText, finalAnswer, false, null, toolRequests);
        }

//        String toolRequest = extractToolRequest(content);
//        boolean hasFinal = !needsFinalization(content);
//        if (state.toolCompleted && state.toolFailureCount == 0 && toolRequest != null) {
//            toolRequest = null;
//            hasFinal = false;
//        }
//        if ((toolRequest != null && hasFinal) || (toolRequest == null && !hasFinal)) {
//            return DefaultAgentEngine.ReasonerParse.invalid(response, toolRequest != null ? "Both TOOL_REQUEST and FINAL present" : "Missing TOOL_REQUEST or FINAL");
//        }
//        List<ToolCall> toolPlan = null;
//        if (toolRequest != null) {
//            ToolCall plan = parseToolPlan(toolRequest);
//            if (plan.name() != null && !plan.name().isBlank()) {
//                toolPlan = List.of(plan);
//            }
//        }
//        return new DefaultAgentEngine.ReasonerParse(response, toolRequest, hasFinal ? extractFinal(response.getContent()) : null, false, null, toolPlan);
//    }

    public static String getRepairMessageIfInvalid(Message message) {
        final String content = message.getContent();
        final List<String> toolRequests = message.getToolRequests();
        final String thoughts = message.getThoughts();

        final boolean finalAnswerAndToolCallsPresent = StringUtils.isNotBlank(content) && CollectionUtils.isNotEmpty(toolRequests);
        final boolean emptyResponse = StringUtils.isBlank(content) && StringUtils.isBlank(thoughts) && CollectionUtils.isEmpty(toolRequests);
        return TemplateUtils.renderForName("repair/invalid_message.txt", Map.of("finalAnswerAndToolCallsPresent", finalAnswerAndToolCallsPresent, "emptyResponse", emptyResponse))
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
        final String finalAnswer = CollectionUtils.getStringValueFromMap(payload, "finalAnswer");
        final String thoughts = CollectionUtils.getStringValueFromMap(payload, "thoughts");
        List<ToolCall> toolRequests = parseToolCallsFromJsonMap(payload);
        return new Message(null, finalAnswer, thoughts);
    }

    private List<ToolCall> parseToolCallsFromJsonMap(Map<String, Object> payload, final Map<String, JSONSchema> toolSchemas) {
        List<Map<String, Object>> toolRequests = CollectionUtils.getListOfMapsFromMap(payload, "toolRequests");
        List<ToolCall> calls = new ArrayList<>();
        for (Map<String, Object> item : toolRequests) {
            final String toolName = CollectionUtils.getStringValueFromMap(item, "name");
            if (StringUtils.isBlank(toolName)) {
                calls.add(new ToolCall(null, toolName, null, STR."Invalid tool name : \{toolName}"));
                continue;
            }
            final Map<String, Object> args = CollectionUtils.getMapFromMap(payload, "args");
            final JSONSchema jsonSchema = toolSchemas.get(toolName);
            final ValidateResult result = jsonSchema.validate(args);
            if (!result.isSuccess()) {
                calls.add(new ToolCall(null, toolName, args, STR."Provided arguments to tool : \{toolName} does not conform to json schema : \{JsonUtils.toJson(jsonSchema.toJSONObject())}"));
                continue;
            }
            calls.add(new ToolCall(null, toolName, args, null));
        }
        return calls;
    }

    private Optional<Map<String, Object>> parseJsonMap(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonUtils.parseMap(text));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
