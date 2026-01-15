package com.localagent.engine;

import com.localagent.engine.context.ContextBuilder;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.ToolCall;
import com.localagent.engine.model.LLMModel;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.state.ToolExecution;
import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.utils.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public class HybridEngine extends AbstractAgentEngine {
    private final LLMModel reasoningModel;
    private final LLMModel toolModel;
    private final Map<String, AgentTool> toolByName;
    private final ContextBuilder reasoningContextBuilder;
    private final ContextBuilder toolContextBuilder;
    private final SessionStore sessionStore;
    private final int invocationLimit;

    public HybridEngine(LLMModel reasoningModel, LLMModel toolModel, List<AgentTool> tools, ContextBuilder reasoningContextBuilder, ContextBuilder toolContextBuilder, SessionStore sessionStore, int invocationLimit) {
        this.reasoningModel = reasoningModel;
        this.toolModel = toolModel;
        this.reasoningContextBuilder = reasoningContextBuilder;
        this.toolContextBuilder = toolContextBuilder;
        this.sessionStore = sessionStore;
        this.invocationLimit = Math.max(1, invocationLimit);
        Map<String, AgentTool> toolMap = new HashMap<>();
        for (AgentTool tool : CollectionUtils.nullSafeList(tools)) {
            toolMap.put(tool.name(), tool);
        }
        this.toolByName = Collections.unmodifiableMap(toolMap);
    }

    @Override
    public Message invoke(final String sessionId, final Message message) {
        sessionStore.appendMessage(getReasoningSessionId(sessionId), message);
        do {
            Message result = runReasoner(sessionId);

            final String finalAnswer = result.getContent();
            if (StringUtils.isNotBlank(finalAnswer)) {
                return message;
            }

            executeToolRequests(sessionId, result.getToolRequests());
        } while (EngineUtils.invocationsThisTurn(sessionStore, sessionId) < invocationLimit);
        return null;
    }

    private Message runReasoner(final String sessionId) {
        invokeListeners(listener -> listener.onReasoningStart(sessionId));
        try {
            return _runReasoner(sessionId, 5);
        } finally {
            invokeListeners(listener -> listener.onReasoningEnd(sessionId));
        }
    }

    private Message _runReasoner(final String sessionId, final int maxRetries) {
        List<Message> prompt = CollectionUtils.nullSafeMutableList(reasoningContextBuilder.buildPrompt(sessionStore, getReasoningSessionId(sessionId)));
        Message response = reasoningModel.generate(prompt);
        response = EngineUtils.sanitizeMessage(response, reasoningModel.format(), reasoningModel.thoughtsStartTag(), reasoningModel.thoughtsEndTag());

        final String repairMessage = EngineUtils.getRepairMessageIfInvalid(response);

        if (StringUtils.isBlank(repairMessage)) {
            return response;
        }
        sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.system(repairMessage));

        if (maxRetries == 0) {
            return Message.system(getReasoningSessionId(repairMessage));
        }
        return _runReasoner(sessionId, maxRetries - 1);
    }

    private static String getReasoningSessionId(final String sessionId) {
        return STR."\{sessionId}_reasoning";
    }

    private List<ToolCall> runToolAssistant(final String sessionId, final List<String> toolRequests) {
        final String message = TemplateUtils.renderForName("json_tool.txt", Map.of("toolRequests", toolRequests));
        sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.user(message));

        int repairAttempts = 0;
        List<ToolCall> toolCalls;
        do {
            List<Message> prompt = toolContextBuilder.buildPrompt(sessionStore, getReasoningSessionId(sessionId));
            Message response = toolModel.generate(prompt);
            response = EngineUtils.sanitizeMessage(response, toolModel.format(), toolModel.thoughtsStartTag(), toolModel.thoughtsEndTag());
            sessionStore.appendMessage(getReasoningSessionId(sessionId), response);
            toolCalls = parseToolCalls(response.getContent());
            if (CollectionUtils.nullSafeList(toolCalls).size() != toolRequests.size()) {
                invokeListeners(listener -> listener.onToolRepair(sessionId));
                // TODO: fix template
                sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.user(TemplateUtils.renderForName("repair/empty_tool_call.txt", Map.of("toolRequests", toolRequests))));
                repairAttempts++;
                if (repairAttempts > 3) {
                    sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.system("You were unable to produce correct tool calls"));
                    break;
                }
            }
        } while (CollectionUtils.isEmpty(toolCalls));

        return toolCalls;
    }

    private void executeToolRequests(final String sessionId, final List<String> toolRequests) {
        if (CollectionUtils.isEmpty(toolRequests)) {
            return;
        }
        final List<ToolCall> toolCalls = runToolAssistant(sessionId, toolRequests);
        invokeListeners(listener -> listener.onToolPlan(sessionId, toolCalls));
        if (CollectionUtils.isEmpty(toolCalls)) {
            return;
        }

        executeTools(sessionId, toolCalls, 5);
    }

    private void executeTools(final String sessionId, final List<ToolCall> toolCalls, final int numRetries) {
        final List<ToolExecution> executions =  _executeTools(toolCalls);
        executions.forEach(toolExecution -> {
            if ("ok".equals(toolExecution.status())) {
                invokeListeners(listener -> listener.onToolExecution(sessionId, toolExecution));
            }
        });
        final Map<String, ToolExecution> toolCallIdVsResult = CollectionUtils.transformToMap(executions, ToolExecution::id, Function.identity());
        final List<ToolCall> failed = new ArrayList<>();

        final Map<String, String> failedToolsVsErrors = new HashMap<>();
        for (final ToolCall toolCall : toolCalls) {
            final ToolExecution toolResult = toolCallIdVsResult.get(toolCall.id());
            if (!"ok".equals(toolResult.status())) {
                failed.add(toolCall);
                failedToolsVsErrors.put(JsonUtils.toJson(toolCall), toolResult.output());
            }
        }


        if (CollectionUtils.isNotEmpty(failed)) {
            if (numRetries > 0) {
                sessionStore.appendMessage(sessionId, Message.system(TemplateUtils.renderForName("tool_failure.txt", Map.of("failures", failedToolsVsErrors))));
            }
            executeTools(sessionId, failed, numRetries - 1);
        }
    }

    private List<ToolExecution> _executeTools(final List<ToolCall> toolCalls) {
        final List<ToolExecution> executions = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            AgentTool tool = toolByName.get(call.name());
            Instant start = Instant.now();
            String status = "ok";
            String output;
            if (tool == null) {
                status = "error";
                output = getReasoningSessionId(call.name());
            } else {
                try {
                    output = tool.execute(call.args());
                } catch (Exception ex) {
                    status = "error";
                    output = STR."Tool error in \{call.name()}: \{ex.getMessage()}";
                }
            }
            Instant end = Instant.now();
            ToolExecution toolExecution = new ToolExecution(call.id(), call.name(), status, output, start, end.toEpochMilli() - start.toEpochMilli());

            executions.add(toolExecution);
        }
        return executions;
    }

    private List<ToolCall> parseToolCalls(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        final Message message = EngineUtils.parseJsonPayload(text);
        return message == null ? null : message.getToolCalls();
    }

}
