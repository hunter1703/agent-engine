package com.localagent.engine;

import com.localagent.engine.model.LLMModel;
import com.localagent.engine.utils.JsonUtils;
import com.localagent.engine.context.ContextBuilder;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import com.localagent.engine.message.ToolCall;
import com.localagent.engine.prompts.PromptTemplates;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.state.ToolExecution;
import com.localagent.engine.tools.AgentTool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultAgentEngine implements AgentEngine {
    private static final Pattern TOOL_REQUEST_BLOCK = Pattern.compile("TOOL_REQUEST:\\s*(.*?)(?:\\n\\s*FINAL:|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FINAL_BLOCK = Pattern.compile("FINAL:\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final LLMModel reasoningModel;
    private final LLMModel toolModel;
    private final LLMModel routerModel;
    private final LLMModel heavyModel;
    private final List<AgentTool> tools;
    private final Map<String, AgentTool> toolByName;
    private final ContextBuilder contextBuilder;
    private final SessionStore stateStore;
    private final int invocationLimit;
    private final int toolRetryLimit;
    private final boolean hybrid;
    private final String thoughtTag;
    private final boolean thoughtsEnabled;
    private final String responseFormat;
    private final String toolCallingFormat;
    private final boolean routingEnabled;
    private final boolean promptTraces;

    public DefaultAgentEngine(
        LLMModel reasoningModel,
        LLMModel toolModel,
        LLMModel routerModel,
        LLMModel heavyModel,
        List<AgentTool> tools,
        ContextBuilder contextBuilder,
        SessionStore stateStore,
        int invocationLimit,
        int toolRetryLimit,
        boolean hybrid,
        String thoughtTag,
        boolean thoughtsEnabled,
        String responseFormat,
        String toolCallingFormat,
        boolean routingEnabled,
        boolean promptTraces
    ) {
        this.reasoningModel = Objects.requireNonNull(reasoningModel, "reasoningModel");
        this.toolModel = toolModel == null ? reasoningModel : toolModel;
        this.routerModel = routerModel == null ? reasoningModel : routerModel;
        this.heavyModel = heavyModel;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextManager");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.invocationLimit = Math.max(1, invocationLimit);
        this.toolRetryLimit = Math.max(0, toolRetryLimit);
        this.hybrid = hybrid;
        this.thoughtTag = thoughtTag == null ? "think" : thoughtTag;
        this.thoughtsEnabled = thoughtsEnabled;
        this.responseFormat = responseFormat;
        this.toolCallingFormat = toolCallingFormat;
        this.routingEnabled = routingEnabled;
        this.promptTraces = promptTraces;
        Map<String, AgentTool> toolMap = new HashMap<>();
        for (AgentTool tool : this.tools) {
            toolMap.put(tool.name(), tool);
        }
        this.toolByName = Collections.unmodifiableMap(toolMap);
    }

    public SessionStore stateStore() {
        return stateStore;
    }

    public ContextBuilder contextManager() {
        return contextBuilder;
    }

    public void setListener(AgentListener callbacks) {
        this.callbacks = callbacks == null ? new AgentListener() {} : callbacks;
    }

    @Override
    public AgentResponse invoke(final String sessionId, final Message message) {
        EngineState state = new EngineState(sessionId);
        while (invocationsThisTurn(sessionId) < invocationLimit) {
            if (hybrid) {
                ReasonerResult result = runReasoner(state);
                if (result.finalAnswer != null) {
                    return new AgentResponse(result.message, result.finalAnswer);
                }
                if (state.pendingToolRequest == null || state.pendingToolRequest.isBlank()) {
                    return new AgentResponse(result.message, extractFinal(result.message.getContent()));
                }
                List<ToolCall> toolCalls = runToolAssistant(state);
                executeTools(state, toolCalls);
            } else {
                AssistantResult result = runAssistant(state);
                if (result.finalAnswer != null) {
                    return new AgentResponse(result.message, result.finalAnswer);
                }
                if (result.toolCalls.isEmpty()) {
                    return new AgentResponse(result.message, extractFinal(result.message.getContent()));
                }
                executeTools(state, result.toolCalls);
            }
        }
        return new AgentResponse(Message.assistant(""), "Invocation limit reached.");
    }

    public String[] splitResponse(String text) {
        if (text == null || text.isBlank()) {
            return new String[] {"", ""};
        }
        String cleaned = text.replaceAll("(?is)<" + Pattern.quote(thoughtTag) + ">.*?</" + Pattern.quote(thoughtTag) + ">", "");
        Matcher finalMatch = FINAL_BLOCK.matcher(cleaned);
        String finalText = finalMatch.find() ? finalMatch.group(1).trim() : cleaned.trim();
        Matcher thoughtMatch = Pattern.compile("(?is)<" + Pattern.quote(thoughtTag) + ">(.*?)</" + Pattern.quote(thoughtTag) + ">")
            .matcher(text);
        String thoughts = thoughtMatch.find() ? thoughtMatch.group(1).trim() : "";
        if (!thoughtsEnabled) {
            return new String[] {"", finalText};
        }
        return new String[] {thoughts, finalText};
    }

    private AssistantResult runAssistant(EngineState state) {
        callbacks.onStatusUpdate("assistant");
        List<Message> prompt = contextBuilder.buildPrompt(new SessionState(state.sessionId, stateStore.getMessages(state.sessionId)));
        recordPromptEvent(state.sessionId, "assistant_prompt", prompt, "initial");
        Message response = reasoningModel.generate(prompt);
        response = maybeStripThoughts(response);
        callbacks.onAssistantFinished(response.getContent());
        appendAssistantMessage(state, response);

        List<ToolCall> toolCalls = parseToolCalls(response.getContent(), isJsonToolMode());
        if (toolCalls.isEmpty()) {
            return new AssistantResult(response, extractFinal(response.getContent()), List.of());
        }
        callbacks.onToolPlan(toolCalls);
        return new AssistantResult(response, null, toolCalls);
    }

    private ReasonerResult runReasoner(EngineState state) {
        callbacks.onStatusUpdate("reasoning");
        List<Message> prompt = contextBuilder.buildPrompt(new SessionState(state.sessionId, stateStore.getMessages(state.sessionId)));
        if (state.toolCompleted && state.toolFailureCount == 0) {
            prompt = appendSystemMessage(prompt, "Tool results are available for this request. Respond with FINAL only.");
        }
        Message failureInstruction = toolFailureInstruction(state.toolFailureCount);
        if (failureInstruction != null) {
            prompt = appendMessage(prompt, failureInstruction);
        }
        String route = routeForState(state, prompt);
        LLMModel model = resolveRoute(route);
        String promptLabel = "heavy".equals(route) ? "heavy_prompt" : "reasoner_prompt";
        recordPromptEvent(state.sessionId, promptLabel, prompt, "initial");

        Message response = model.generate(prompt);
        response = maybeStripThoughts(response);
        callbacks.onAssistantFinished(response.getContent());
        recordEvent(state.sessionId, Map.of("type", "reasoner_response", "content", response.getContent()));

        ReasonerParse parse = validateReasonerResponse(state, response);
        Message finalMessage = parse.message;
        appendAssistantMessage(state, finalMessage);
        state.pendingToolRequest = parse.toolRequest;
        if (parse.toolRequest != null && parse.toolPlan != null) {
            callbacks.onToolPlan(parse.toolPlan);
        }
        return new ReasonerResult(finalMessage, parse.finalAnswer);
    }

    private ReasonerParse validateReasonerResponse(EngineState state, Message response) {
        int attempts = 0;
        String userText = lastHumanText(state.sessionId);
        Message toolsMessage = contextBuilder.toolMessage();
        boolean recordedInvalid = false;
        boolean recordedRepair = false;
        ReasonerParse parsed = parseReasonerResponse(state, response);
        while (parsed.invalid && attempts < 3) {
            attempts++;
            String reason = parsed.reason;
            if (!recordedInvalid) {
                appendSystemMessage(state.sessionId, "Invalid assistant response (for correction):\n" + response.getContent());
                recordedInvalid = true;
            }
            if (!recordedRepair) {
                appendSystemMessage(state.sessionId, "System note: Previous assistant response was invalid (" + reason + "). Follow the protocol: respond with TOOL_REQUEST or FINAL only.");
                recordedRepair = true;
            }
            callbacks.onStatus("repairing: " + reason + "\n" + response.getContent());
            recordEvent(state.sessionId, Map.of("type", "reasoner_repair_attempt", "reason", reason, "content", response.getContent()));
            callbacks.onStatusUpdate("repairing");
            List<Message> repairPrompt = new ArrayList<>();
            repairPrompt.add(buildRepairMessage());
            if (toolsMessage != null) {
                repairPrompt.add(toolsMessage);
            }
            String toolNote = state.toolCompleted ? "Tool results are available." : "";
            repairPrompt.add(Message.user("User request:\n" + userText + "\n\n" + toolNote + "\nInvalid response:\n" + response.getContent()));
            recordPromptEvent(state.sessionId, "repair_prompt", repairPrompt, "repair_" + attempts);
            response = reasoningModel.generate(repairPrompt);
            response = maybeStripThoughts(response);
            recordEvent(state.sessionId, Map.of("type", "reasoner_repair_response", "content", response.getContent()));
            parsed = parseReasonerResponse(state, response);
        }

        Message resultMessage = parsed.message;
        if (parsed.toolRequest != null && state.toolFailureCount >= toolRetryLimit) {
            String finalized = finalizeAnswer(resultMessage.getContent());
            if (!finalized.isBlank()) {
                resultMessage = Message.assistant(prefixFinal(finalized));
                parsed = parsed.withFinal(extractFinal(resultMessage.getContent()));
            }
        }
        if (parsed.toolRequest == null && needsFinalization(resultMessage.getContent())) {
            String finalized = finalizeAnswer(resultMessage.getContent());
            if (!finalized.isBlank()) {
                resultMessage = Message.assistant(prefixFinal(finalized));
                parsed = parsed.withFinal(extractFinal(resultMessage.getContent()));
            }
        }
        return parsed.withMessage(resultMessage);
    }

    private ReasonerParse parseReasonerResponse(EngineState state, Message response) {
        String content = contentForValidation(response.getContent());
        if (isJsonResponse()) {
            ParsedJsonPayload payload = parseJsonPayload(content);
            if (payload.payload == null) {
                return ReasonerParse.invalid(response, "Invalid JSON response");
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
                return ReasonerParse.invalid(response, hasToolRequest ? "Both TOOL_REQUEST and FINAL present" : "Missing TOOL_REQUEST or FINAL");
            }
            String toolRequestText = toolRequestTextFromJson(toolRequests);
            Message message = response;
            if (toolRequestText != null) {
                message = Message.assistant("TOOL_REQUEST:\n" + toolRequestText);
            } else if (finalAnswer != null) {
                message = Message.assistant("FINAL: " + finalAnswer);
            }
            return new ReasonerParse(message, toolRequestText, finalAnswer, false, null, toolRequests);
        }

        String toolRequest = extractToolRequest(content);
        boolean hasFinal = !needsFinalization(content);
        if (state.toolCompleted && state.toolFailureCount == 0 && toolRequest != null) {
            toolRequest = null;
            hasFinal = false;
        }
        if ((toolRequest != null && hasFinal) || (toolRequest == null && !hasFinal)) {
            return ReasonerParse.invalid(response, toolRequest != null ? "Both TOOL_REQUEST and FINAL present" : "Missing TOOL_REQUEST or FINAL");
        }
        List<ToolCall> toolPlan = null;
        if (toolRequest != null) {
            ToolCall plan = parseToolPlan(toolRequest);
            if (plan.name() != null && !plan.name().isBlank()) {
                toolPlan = List.of(plan);
            }
        }
        return new ReasonerParse(response, toolRequest, hasFinal ? extractFinal(response.getContent()) : null, false, null, toolPlan);
    }

    private List<ToolCall> runToolAssistant(EngineState state) {
        callbacks.onStatusUpdate("tool_calling");
        List<Message> prompt = contextBuilder.buildPrompt(new SessionState(state.sessionId, stateStore.getMessages(state.sessionId)));
        Message protocol = contextBuilder.protocolMessage();
        List<Message> filtered = new ArrayList<>();
        for (Message message : prompt) {
            if (protocol != null && message.getRole() == Role.SYSTEM && message.getContent().equals(protocol.getContent())) {
                continue;
            }
            filtered.add(message);
        }
        String toolTemplate = loadToolTemplate();
        Message toolCallMessage = Message.system(PromptTemplates.renderTemplate(toolTemplate, Map.of("thought_tag", thoughtTag, "thoughts_enabled", thoughtsEnabled)));
        List<Message> toolPrompt = new ArrayList<>();
        toolPrompt.add(toolCallMessage);
        toolPrompt.addAll(filtered);
        toolPrompt.add(Message.user("TOOL_REQUEST:\n" + state.pendingToolRequest));

        recordPromptEvent(state.sessionId, "tool_prompt", toolPrompt, "initial");
        Message response = toolModel.generate(toolPrompt);
        recordEvent(state.sessionId, Map.of("type", "tool_response", "content", response.getContent()));
        List<ToolCall> toolCalls = parseToolCalls(response.getContent(), isJsonToolMode());
        int repairAttempts = 0;
        while (hybrid && toolCalls.isEmpty() && repairAttempts < 2) {
            repairAttempts++;
            callbacks.onStatus("tool_repair: missing tool_calls\n" + response.getContent());
            recordEvent(state.sessionId, Map.of("type", "tool_repair_attempt", "content", response.getContent()));
            callbacks.onStatusUpdate("tool_repair");
            List<Message> repairPrompt = new ArrayList<>();
            repairPrompt.add(toolCallMessage);
            repairPrompt.add(buildToolRepairMessage());
            repairPrompt.addAll(filtered);
            repairPrompt.add(Message.user("TOOL_REQUEST:\n" + state.pendingToolRequest));
            recordPromptEvent(state.sessionId, "tool_prompt", repairPrompt, "repair_" + repairAttempts);
            response = toolModel.generate(repairPrompt);
            recordEvent(state.sessionId, Map.of("type", "tool_repair_response", "content", response.getContent()));
            toolCalls = parseToolCalls(response.getContent(), isJsonToolMode());
        }
        appendAssistantMessage(state, response);
        state.pendingToolRequest = null;
        return toolCalls;
    }

    private void executeTools(EngineState state, List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            state.toolFailureCount += 1;
            state.toolCompleted = false;
            return;
        }
        callbacks.onStatusUpdate("tool_exec");
        int parentIndex = state.lastAssistantIndex;
        for (ToolCall call : toolCalls) {
            AgentTool tool = toolByName.get(call.name());
            Instant start = Instant.now();
            String status = "ok";
            String output;
            if (tool == null) {
                status = "error";
                output = "Unknown tool: " + call.name();
            } else {
                try {
                    output = tool.execute(call.args());
                } catch (Exception ex) {
                    status = "error";
                    output = "Tool error in " + call.name() + ": " + ex.getMessage();
                }
            }
            Instant end = Instant.now();
            ToolExecution record = new ToolExecution(
                call.name(),
                call.args(),
                output,
                status,
                start,
                end.toEpochMilli() - start.toEpochMilli(),
                null
            );
            stateStore.recordToolExecution(state.sessionId, parentIndex, record);
            callbacks.onToolExec(record);
            if (isToolFailure(status, output)) {
                state.toolFailureCount += 1;
            } else {
                state.toolFailureCount = 0;
            }
            stateStore.appendMessage(state.sessionId, Message.tool(output, call.name(), call.args()));
            contextBuilder.onToolResult(new SessionState(state.sessionId, stateStore.getMessages(state.sessionId)), call, output);
        }
        state.toolCompleted = true;
    }

    private String routeForState(EngineState state, List<Message> prompt) {
        if (!routingEnabled) {
            return "reasoning";
        }
        int currentTurnIndex = lastHumanIndex(state.sessionId);
        if (state.routerChoice != null && state.routerTurnIndex == currentTurnIndex) {
            return state.routerChoice;
        }
        String userText = lastHumanText(state.sessionId);
        if (userText.isBlank()) {
            return "reasoning";
        }
        List<String> routes = new ArrayList<>();
        routes.add("reasoning");
        if (hybrid) {
            routes.add("tool");
        }
        if (heavyModel != null) {
            routes.add("heavy");
        }
        String routerTemplate = PromptTemplates.load("router.txt");
        String routeOptions = String.join("|", routes);
        String rendered = PromptTemplates.renderTemplate(routerTemplate, Map.of(
            "route_options", routeOptions,
            "tool_enabled", routes.contains("tool"),
            "heavy_enabled", routes.contains("heavy")
        ));
        List<Message> routerPrompt = List.of(
            Message.system(rendered),
            Message.user("User message:\n" + userText)
        );
        recordPromptEvent(state.sessionId, "router_prompt", routerPrompt, "initial");
        callbacks.onStatusUpdate("routing");
        Message response = routerModel.generate(routerPrompt);
        String route = parseRoute(response.getContent());
        if (!routes.contains(route)) {
            route = "reasoning";
        }
        state.routerChoice = route;
        state.routerTurnIndex = currentTurnIndex;
        recordEvent(state.sessionId, Map.of("type", "router_decision", "route", route, "turn_index", currentTurnIndex));
        return route;
    }

    private String parseRoute(String text) {
        ParsedJsonPayload payload = parseJsonPayload(text);
        if (payload.payload != null && payload.payload.get("route") != null) {
            return String.valueOf(payload.payload.get("route")).trim().toLowerCase();
        }
        return "reasoning";
    }

    private LLMModel resolveRoute(String route) {
        if ("tool".equals(route)) {
            return toolModel;
        }
        if ("heavy".equals(route) && heavyModel != null) {
            return heavyModel;
        }
        return reasoningModel;
    }

    private Message maybeStripThoughts(Message response) {
        if (thoughtsEnabled) {
            return response;
        }
        String cleaned = stripThoughtTags(response.getContent());
        if (!cleaned.equals(response.getContent())) {
            return Message.assistant(cleaned);
        }
        return response;
    }

    private String stripThoughtTags(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?is)</?" + Pattern.quote(thoughtTag) + ">", "").trim();
    }

    private String contentForValidation(String text) {
        return stripThoughtBlock(text);
    }

    private String stripThoughtBlock(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("(?is)<" + Pattern.quote(thoughtTag) + ">.*?</" + Pattern.quote(thoughtTag) + ">", "").trim();
    }

    private Message toolFailureInstruction(int failureCount) {
        if (failureCount <= 0) {
            return null;
        }
        int remaining = Math.max(0, toolRetryLimit - failureCount);
        return Message.system(
            "Tool execution failed or returned empty/incorrect data. Try a workaround (different arguments, smaller scope, "
                + "alternative tool) and attempt at most " + remaining + " more time(s). If retries are exhausted, ask the user "
                + "for clarification or constraints."
        );
    }

    private String finalizeAnswer(String draft) {
        List<Message> prompt = List.of(
            Message.system(
                "You previously produced a draft with THOUGHTS and possibly an empty FINAL. Return ONLY a FINAL section now. "
                    + "Do not include THOUGHTS or any other text. Begin with 'FINAL:' and provide the user-visible answer. "
                    + "If you cannot answer, say why in FINAL and what is needed."
            ),
            Message.user("Draft to finalize:\n---\n" + draft + "\n---\nNow produce FINAL only.")
        );
        Message response = reasoningModel.generate(prompt);
        if (isJsonResponse()) {
            ParsedJsonPayload payload = parseJsonPayload(response.getContent());
            if (payload.payload != null) {
                Object finalAnswer = payload.payload.get("finalAnswer");
                if (finalAnswer != null) {
                    return finalAnswer.toString();
                }
            }
        }
        return response.getContent();
    }

    private String prefixFinal(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.toUpperCase().startsWith("FINAL:")) {
            return trimmed;
        }
        return "FINAL: " + trimmed;
    }

    private boolean needsFinalization(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        Matcher match = FINAL_BLOCK.matcher(text.trim());
        if (!match.find()) {
            return true;
        }
        return match.group(1) == null || match.group(1).trim().isEmpty();
    }

    private String extractFinal(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = FINAL_BLOCK.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    private String extractToolRequest(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = TOOL_REQUEST_BLOCK.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private ToolCall parseToolPlan(String toolRequestBlock) {
        String toolName = null;
        Map<String, Object> args = new HashMap<>();
        for (String line : toolRequestBlock.split("\\n")) {
            String cleaned = line.trim();
            if (cleaned.startsWith("-")) {
                cleaned = cleaned.substring(1).trim();
            }
            int colon = cleaned.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = cleaned.substring(0, colon).trim().toLowerCase();
            String value = cleaned.substring(colon + 1).trim();
            if ("tool".equals(key)) {
                toolName = value;
            } else if ("inputs".equals(key)) {
                parseJsonMap(value).ifPresent(args::putAll);
            } else if ("intent".equals(key) || "constraints".equals(key)) {
                args.put(key, value);
            }
        }
        if (toolName == null) {
            toolName = "";
        }
        return new ToolCall(toolName, args);
    }

    private List<ToolCall> parseToolCalls(String text, boolean jsonMode) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (jsonMode) {
            ParsedJsonPayload payload = parseJsonPayload(text);
            if (payload.payload != null && payload.payload.containsKey("tool_name")) {
                String name = String.valueOf(payload.payload.get("tool_name"));
                Object argsValue = payload.payload.get("tool_args");
                Map<String, Object> args = argsValue instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
                return List.of(new ToolCall(name, args));
            }
        }
        if (text.contains("TOOL_REQUEST")) {
            String request = extractToolRequest(text);
            if (request != null) {
                ToolCall call = parseToolPlan(request);
                if (call.name() != null && !call.name().isBlank()) {
                    return List.of(call);
                }
            }
        }
        if (text.contains("tool:") && text.contains("inputs")) {
            ToolCall call = parseToolPlan(text);
            if (call.name() != null && !call.name().isBlank()) {
                return List.of(call);
            }
        }
        return parseToolCallsFromJson(text).orElse(List.of());
    }

    private Optional<List<ToolCall>> parseToolCallsFromJson(String text) {
        ParsedJsonPayload payload = parseJsonPayload(text);
        if (payload.payload == null) {
            return Optional.empty();
        }
        Object toolRequests = payload.payload.get("toolRequests");
        if (!(toolRequests instanceof List<?> list)) {
            return Optional.empty();
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
            calls.add(new ToolCall(nameValue.toString(), args));
        }
        return Optional.of(calls);
    }

    private String toolRequestTextFromJson(List<ToolCall> toolRequests) {
        if (toolRequests == null || toolRequests.isEmpty()) {
            return null;
        }
        ToolCall primary = toolRequests.get(0);
        String argsText = "{}";
        try {
            argsText = JsonUtils.toJson(primary.args());
        } catch (Exception ignored) {
            argsText = "{}";
        }
        return "- tool: " + primary.name() + "\n- inputs: " + argsText;
    }

    private ParsedJsonPayload parseJsonPayload(String text) {
        if (text == null || text.isBlank()) {
            return new ParsedJsonPayload(null, null, List.of());
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
            payload = JsonUtils.parseMap(cleaned);
        } catch (Exception ex) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    payload = JsonUtils.parseMap(cleaned.substring(start, end + 1));
                } catch (Exception ignored) {
                    payload = null;
                }
            }
        }
        if (payload == null) {
            return new ParsedJsonPayload(null, null, List.of());
        }
        Object finalAnswer = payload.get("finalAnswer");
        List<ToolCall> toolRequests = parseToolCallsFromJsonMap(payload);
        return new ParsedJsonPayload(payload, finalAnswer == null ? null : finalAnswer.toString(), toolRequests);
    }

    private List<ToolCall> parseToolCallsFromJsonMap(Map<String, Object> payload) {
        Object toolRequests = payload.get("toolRequests");
        if (!(toolRequests instanceof List<?> list)) {
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
            calls.add(new ToolCall(nameValue.toString(), args));
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

    private boolean isJsonResponse() {
        return "json".equalsIgnoreCase(responseFormat);
    }

    private boolean isJsonToolMode() {
        return "json".equalsIgnoreCase(toolCallingFormat);
    }

    private Message buildRepairMessage() {
        String templateName = isJsonResponse() ? "json.txt" : "base.txt";
        String template = PromptTemplates.load(templateName);
        String rendered = PromptTemplates.renderTemplate(template, Map.of("thought_tag", thoughtTag, "thoughts_enabled", thoughtsEnabled));
        return Message.system(rendered);
    }

    private Message buildToolRepairMessage() {
        String templateName = isJsonToolMode() ? "json_tool.txt" : "tool.txt";
        String template = PromptTemplates.load(templateName);
        String rendered = PromptTemplates.renderTemplate(template, Map.of("thought_tag", thoughtTag, "thoughts_enabled", thoughtsEnabled));
        return Message.system(rendered);
    }

    private String loadToolTemplate() {
        return PromptTemplates.load(isJsonToolMode() ? "json_tool.txt" : "tool.txt");
    }

    private void appendAssistantMessage(EngineState state, Message message) {
        int index = stateStore.appendMessage(state.sessionId, message);
        stateStore.incrementSteps(state.sessionId, 1);
        state.lastAssistantIndex = index;
    }

    private void appendSystemMessage(String sessionId, String content) {
        stateStore.appendMessage(sessionId, Message.system(content));
    }

    private List<Message> appendSystemMessage(List<Message> prompt, String content) {
        List<Message> result = new ArrayList<>(prompt);
        result.add(Message.system(content));
        return result;
    }

    private List<Message> appendMessage(List<Message> prompt, Message message) {
        List<Message> result = new ArrayList<>(prompt);
        result.add(message);
        return result;
    }

    private boolean isToolFailure(String status, String result) {
        if (!"ok".equals(status)) {
            return true;
        }
        return result == null || result.isBlank();
    }

    private int lastHumanIndex(String sessionId) {
        List<Message> messages = stateStore.getMessages(sessionId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == Role.USER) {
                return i;
            }
        }
        return -1;
    }

    private String lastHumanText(String sessionId) {
        List<Message> messages = stateStore.getMessages(sessionId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == Role.USER) {
                return messages.get(i).getContent();
            }
        }
        return "";
    }

    private void recordPromptEvent(String sessionId, String promptType, List<Message> messages, String reason) {
        if (!promptTraces) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            builder.append("[").append(message.getRole().name().toLowerCase()).append("] ")
                .append(message.getContent()).append("\n");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", promptType);
        payload.put("content", builder.toString().trim());
        if (reason != null) {
            payload.put("reason", reason);
        }
        recordEvent(sessionId, payload);
    }

    private void recordEvent(String sessionId, Map<String, Object> payload) {
        stateStore.recordEvent(sessionId, payload);
    }

    private record ReasonerResult(Message message, String finalAnswer) {
    }

    private record AssistantResult(Message message, String finalAnswer, List<ToolCall> toolCalls) {
    }

    private record ParsedJsonPayload(Map<String, Object> payload, String finalAnswer, List<ToolCall> toolRequests) {
    }

    private static final class EngineState {
        private final String sessionId;
        private int lastAssistantIndex = -1;
        private String pendingToolRequest;
        private int toolFailureCount = 0;
        private boolean toolCompleted = false;
        private String routerChoice;
        private int routerTurnIndex = -1;

        private EngineState(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    private record ReasonerParse(
        Message message,
        String toolRequest,
        String finalAnswer,
        boolean invalid,
        String reason,
        List<ToolCall> toolPlan
    ) {
        static ReasonerParse invalid(Message message, String reason) {
            return new ReasonerParse(message, null, null, true, reason, null);
        }

        ReasonerParse withMessage(Message updated) {
            return new ReasonerParse(updated, toolRequest, finalAnswer, invalid, reason, toolPlan);
        }

        ReasonerParse withFinal(String finalAnswer) {
            return new ReasonerParse(message, null, finalAnswer, false, null, null);
        }
    }
}
