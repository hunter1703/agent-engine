package com.agentengine.engine;

import static com.agentengine.engine.utils.ResourceUtils.loadResourceAsString;
import static java.lang.StringTemplate.STR;

import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.state.SessionStore;
import com.agentengine.engine.tools.AgentTool;
import com.agentengine.engine.utils.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public class HybridEngine extends AbstractAgentEngine {
  private static final String MISSING_TOOL_OR_FINAL_MESSAGE =
      "You must provide a final answer or tool requests as defined in protocol.";
  private final LLMModel reasoningModel;
  private final LLMModel toolAssistantModel;
  private final Map<String, AgentTool> toolByName;
  private final ContextBuilder reasoningContextBuilder;
  private final ContextBuilder toolAssistantContextBuilder;
  private final SessionStore sessionStore;
  private final int invocationLimit;

  public HybridEngine(final LLMModel reasoningModel, final LLMModel toolAssistantModel, final List<AgentTool> tools,
      final ContextBuilder reasoningContextBuilder, final ContextBuilder toolAssistantContextBuilder,
      final SessionStore sessionStore, final int invocationLimit) {
    this.reasoningModel = reasoningModel;
    this.toolAssistantModel = toolAssistantModel;
    this.reasoningContextBuilder = reasoningContextBuilder;
    this.toolAssistantContextBuilder = toolAssistantContextBuilder;
    this.sessionStore = sessionStore;
    this.invocationLimit = Math.max(1, invocationLimit);
    final Map<String, AgentTool> toolMap = new HashMap<>();
    for (AgentTool tool : CollectionUtils.nullSafeList(tools)) {
      toolMap.put(tool.name(), tool);
    }
    this.toolByName = Collections.unmodifiableMap(toolMap);
  }

  @Override
  public Message invoke(final String sessionId, final Message message) {
    sessionStore.appendMessage(getReasoningSessionId(sessionId), message);
    Message finalResponse = null;
    do {
      final Message result = runReasoner(sessionId);

      final String finalAnswer = result.getContent();
      if (StringUtils.isNotBlank(finalAnswer)) {
        invokeListeners(listener -> listener.onFinalAnswer(sessionId, result));
        finalResponse = result;
        break;
      }

      final List<String> toolRequests = result.getToolRequests();
      if (CollectionUtils.isEmpty(toolRequests)) {
        sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.system(MISSING_TOOL_OR_FINAL_MESSAGE));
        continue;
      }
      executeToolRequests(sessionId, result.getId(), toolRequests);
    } while (EngineUtils.invocationsThisTurn(sessionStore, getReasoningSessionId(sessionId))
        < invocationLimit);

    if (finalResponse != null) {
      return finalResponse;
    }
    final Message invocationsExceededMessage =
        Message.system(STR."Number of assistant invocations exceeded maximum : \{invocationLimit}");
    invokeListeners(listener -> listener.onFinalAnswer(sessionId, invocationsExceededMessage));
    return invocationsExceededMessage;
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return reasoningContextBuilder.buildPrompt(getReasoningSessionId(sessionId));
  }

  private Message runReasoner(final String sessionId) {
    invokeListeners(listener -> listener.onReasoningStart(sessionId));
    Message message = null;
    try {
      message = _runReasoner(sessionId, 5);
    } finally {
      final Message reasonerMessage = message;
      invokeListeners(listener -> listener.onReasoningEnd(sessionId, reasonerMessage));
    }
    return message;
  }

  private Message _runReasoner(final String sessionId, final int maxRetries) {
    final List<Message> prompt = CollectionUtils
        .nullSafeMutableList(reasoningContextBuilder.buildPrompt(getReasoningSessionId(sessionId)));
    Message response = reasoningModel.generate(prompt);
    response = EngineUtils.sanitizeMessage(response, reasoningModel.responseFormat(), reasoningModel.thoughtsEnabled(),
        reasoningModel.thoughtsStartTag(), reasoningModel.thoughtsEndTag());
    sessionStore.appendMessage(getReasoningSessionId(sessionId), response);

    final String repairMessage = EngineUtils.getRepairMessageIfInvalid(response);

    if (StringUtils.isBlank(repairMessage)) {
      return response;
    }
    sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.system(repairMessage));

    if (maxRetries == 0) {
      return response;
    }
    return _runReasoner(sessionId, maxRetries - 1);
  }

  private static String getReasoningSessionId(final String sessionId) {
    return STR."\{sessionId}_reasoning";
  }

  private static String getToolSessionId(final String sessionId) {
    return STR."\{sessionId}_tool";
  }

  private List<ToolCall> runToolAssistant(final String sessionId, final List<String> toolRequests) {
    final String message = TemplateUtils.renderTemplateForName("hybrid/tool_assistant_json.txt", Map.of("toolRequests",
        toolRequests, "tool_schema", loadResourceAsString("/schemas/hybrid/tool_call_schema.json")));
    sessionStore.appendMessage(getToolSessionId(sessionId), Message.user(message));

    final List<ToolRequest> requestInfos = EngineUtils.parseToolRequestInfo(toolRequests);
    final Map<String, ToolCall> matchedCallsById = new HashMap<>();
    List<ToolRequest> remainingRequests = new ArrayList<>(requestInfos);
    int repairAttempts = 0;
    List<ToolCall> toolCalls = null;
    do {
      final List<Message> prompt = toolAssistantContextBuilder.buildPrompt(getToolSessionId(sessionId));
      Message response = toolAssistantModel.generate(prompt);
      response = EngineUtils.sanitizeMessage(response, toolAssistantModel.responseFormat(),
          toolAssistantModel.thoughtsEnabled(), toolAssistantModel.thoughtsStartTag(),
          toolAssistantModel.thoughtsEndTag());
      sessionStore.appendMessage(getToolSessionId(sessionId), response);
      toolCalls = CollectionUtils.nullSafeList(response.getToolCalls());
      if (toolCalls.isEmpty()) {
        toolCalls = parseToolCalls(response.getContent());
      }
      final Map<String, ToolCall> newlyMatched = selectMatchingToolCalls(toolCalls, remainingRequests);
      newlyMatched.forEach(matchedCallsById::putIfAbsent);
      remainingRequests = unresolvedRequests(requestInfos, matchedCallsById);
      if (CollectionUtils.isNotEmpty(remainingRequests)) {
        final List<ToolCall> newToolCalls = CollectionUtils.nullSafeList(toolCalls);
        final List<ToolRequest> newRemainingRequests = CollectionUtils.nullSafeList(remainingRequests);
        invokeListeners(listener -> listener.onToolRepair(sessionId, newToolCalls, newRemainingRequests));
        final List<String> missingRequests = remainingRequests.stream().map(ToolRequest::raw).toList();
        sessionStore.appendMessage(getToolSessionId(sessionId), Message.user(TemplateUtils
            .renderTemplateForName("hybrid/repair/empty_tool_call.txt", Map.of("toolRequests", missingRequests))));
        repairAttempts++;
        if (repairAttempts > 3) {
          sessionStore.appendMessage(getReasoningSessionId(sessionId),
              Message.system("You were unable to produce correct tool calls"));
          break;
        }
      }
    } while (!remainingRequests.isEmpty());

    if (!remainingRequests.isEmpty()) {
      return List.of();
    }
    return buildOrderedToolCalls(requestInfos, matchedCallsById);
  }

  private void executeToolRequests(final String sessionId, final String reasoningMessageId,
      final List<String> toolRequests) {
    if (CollectionUtils.isEmpty(toolRequests)) {
      return;
    }
    executeToolPlan(sessionId, reasoningMessageId, toolRequests, 5);
  }

  private void executeToolPlan(final String sessionId, final String reasoningMessageId, final List<String> toolRequests,
      final int numRetries) {
    final List<ToolCall> toolCalls = runToolAssistant(sessionId, toolRequests);
    invokeListeners(listener -> listener.onToolPlan(sessionId, toolCalls));
    if (CollectionUtils.isEmpty(toolCalls)) {
      return;
    }
    final List<ToolExecution> executions = _executeTools(toolCalls);
    sessionStore.addToolExecutions(getReasoningSessionId(sessionId), reasoningMessageId, executions);
    executions.forEach(toolExecution -> {
      if ("ok".equals(toolExecution.getStatus())) {
        invokeListeners(listener -> listener.onToolExecution(sessionId, toolExecution));
      }
    });
    final Map<String, ToolExecution> toolCallIdVsResult = CollectionUtils.transformToMap(executions,
        execution -> execution.getToolCall() == null ? null : execution.getToolCall().id(), Function.identity());
    final List<ToolCall> failed = new ArrayList<>();
    final List<String> failedToolRequests = new ArrayList<>();
    final Map<String, String> failedToolsVsErrors = new HashMap<>();
    for (int i = 0; i < toolCalls.size(); i++) {
      final ToolCall toolCall = toolCalls.get(i);
      final ToolExecution toolResult = toolCallIdVsResult.get(toolCall.id());
      if (toolResult == null || !"ok".equals(toolResult.getStatus())) {
        failed.add(toolCall);
        failedToolsVsErrors.put(JsonUtils.toJson(toolCall),
            toolResult == null ? "Missing tool execution" : toolResult.getOutput());
        failedToolRequests.add(toolRequests.get(i));
      }
    }

    if (CollectionUtils.isNotEmpty(failed) && numRetries > 0) {
      final String failureMessage = TemplateUtils.renderTemplateForName("shared/tool_failure.txt",
          Map.of("failures", failedToolsVsErrors));
      sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.system(failureMessage));
      sessionStore.appendMessage(getToolSessionId(sessionId), Message.system(failureMessage));
      executeToolPlan(sessionId, reasoningMessageId, failedToolRequests.isEmpty() ? toolRequests : failedToolRequests,
          numRetries - 1);
    }
  }

  private List<ToolExecution> _executeTools(final List<ToolCall> toolCalls) {
    final List<ToolExecution> executions = new ArrayList<>();
    for (ToolCall call : toolCalls) {
      final AgentTool tool = toolByName.get(call.name());
      final Instant start = Instant.now();
      String status = "ok";
      String output;
      if (tool == null) {
        status = "error";
        output = STR."Unknown tool: \{call.name()}";
      } else {
        try {
          output = tool.execute(call.args());
        } catch (Exception ex) {
          status = "error";
          output = STR."Tool error in \{call.name()}: \{ex.getMessage()}";
        }
      }
      final Instant end = Instant.now();
      final ToolExecution toolExecution =
          new ToolExecution(call, status, output, start, end.toEpochMilli() - start.toEpochMilli());

      executions.add(toolExecution);
    }
    return executions;
  }

  private List<ToolCall> parseToolCalls(final String text) {
    if (StringUtils.isBlank(text)) {
      return List.of();
    }
    final Message parsed = EngineUtils.parseJsonPayload(text);
    if (parsed == null) {
      return List.of();
    }
    return CollectionUtils.nullSafeList(parsed.getToolCalls());
  }

  private Map<String, ToolCall> selectMatchingToolCalls(final List<ToolCall> toolCalls,
      final List<ToolRequest> toolRequests) {
    if (CollectionUtils.isEmpty(toolCalls) || CollectionUtils.isEmpty(toolRequests)) {
      return Map.of();
    }
    final Map<String, ToolCall> toolCallsById = new HashMap<>();
    for (ToolCall call : toolCalls) {
      if (StringUtils.isBlank(call.id()) || StringUtils.isBlank(call.name())) {
        continue;
      }
      toolCallsById.putIfAbsent(call.id(), call);
    }
    final Map<String, ToolCall> matched = new HashMap<>();
    for (ToolRequest requestInfo : toolRequests) {
      if (StringUtils.isBlank(requestInfo.id()) || StringUtils.isBlank(requestInfo.name())) {
        continue;
      }
      final ToolCall call = toolCallsById.get(requestInfo.id());
      if (call != null && requestInfo.name().equals(call.name())) {
        matched.putIfAbsent(requestInfo.id(), call);
      }
    }
    return matched;
  }

  private List<ToolRequest> unresolvedRequests(final List<ToolRequest> toolRequests,
      final Map<String, ToolCall> matchedCalls) {
    if (CollectionUtils.isEmpty(toolRequests)) {
      return List.of();
    }
    final List<ToolRequest> remaining = new ArrayList<>();
    for (ToolRequest requestInfo : toolRequests) {
      if (StringUtils.isBlank(requestInfo.id()) || StringUtils.isBlank(requestInfo.name())) {
        remaining.add(requestInfo);
        continue;
      }
      final ToolCall call = matchedCalls.get(requestInfo.id());
      if (call == null || !requestInfo.name().equals(call.name())) {
        remaining.add(requestInfo);
      }
    }
    return remaining;
  }

  private List<ToolCall> buildOrderedToolCalls(final List<ToolRequest> toolRequests,
      final Map<String, ToolCall> matchedCalls) {
    if (CollectionUtils.isEmpty(toolRequests) || CollectionUtils.isEmpty(matchedCalls)) {
      return List.of();
    }
    final List<ToolCall> ordered = new ArrayList<>();
    for (ToolRequest requestInfo : toolRequests) {
      if (StringUtils.isBlank(requestInfo.id()) || StringUtils.isBlank(requestInfo.name())) {
        continue;
      }
      final ToolCall call = matchedCalls.get(requestInfo.id());
      if (call != null && requestInfo.name().equals(call.name())) {
        ordered.add(call);
      }
    }
    return ordered;
  }
}
