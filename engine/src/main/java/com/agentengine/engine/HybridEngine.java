package com.agentengine.engine;

import static com.agentengine.commons.utils.ResourceUtils.loadResourceAsString;
import static java.lang.StringTemplate.STR;

import com.agentengine.commons.utils.CollectionUtils;
import com.agentengine.commons.utils.JsonUtils;
import com.agentengine.commons.utils.StringUtils;
import com.agentengine.commons.utils.TemplateUtils;
import com.agentengine.engine.client.beans.session.ToolExecution;
import com.agentengine.engine.client.beans.session.Message;
import com.agentengine.engine.client.beans.session.ToolCall;
import com.agentengine.engine.client.state.SessionStore;
import com.agentengine.engine.client.beans.session.ToolRequest;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.utils.EngineUtils;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HybridEngine extends AbstractAgentEngine {
  private static final String MISSING_TOOL_AND_FINAL_MESSAGE = "You must provide at least a final answer or tool requests as defined in protocol.";
  private final LLMModel reasoningModel;
  private final LLMModel toolAssistantModel;
  private final Map<String, Tool> toolByName;
  private final ContextBuilder reasoningContextBuilder;
  private final ContextBuilder toolAssistantContextBuilder;
  private final SessionStore sessionStore;
  private final int invocationLimit;

  public HybridEngine(final LLMModel reasoningModel, final LLMModel toolAssistantModel, final List<Tool> tools,
      final ContextBuilder reasoningContextBuilder, final ContextBuilder toolAssistantContextBuilder,
      final SessionStore sessionStore, final int invocationLimit) {
    this.reasoningModel = reasoningModel;
    this.toolAssistantModel = toolAssistantModel;
    this.reasoningContextBuilder = reasoningContextBuilder;
    this.toolAssistantContextBuilder = toolAssistantContextBuilder;
    this.sessionStore = sessionStore;
    this.invocationLimit = Math.max(1, invocationLimit);
    final Map<String, Tool> toolMap = new HashMap<>();
    for (Tool tool : CollectionUtils.nullSafeList(tools)) {
      toolMap.put(tool.name(), tool);
    }
    this.toolByName = Collections.unmodifiableMap(toolMap);
  }

  @Override
  public Message invoke(final String sessionId, final Message message) {
    appendUserMessage(getReasoningSessionId(sessionId), message);
    appendUserMessage(getToolSessionId(sessionId), message);
    Message finalResponse = null;
    do {
      final Message result;
      try {
        result = runReasoner(sessionId);
      } catch (Exception ex) {
        final Message failure = Message.system(STR."Reasoner failed: \{ex.getMessage()}");
        invokeListeners(listener -> listener.onFinalAnswer(sessionId, failure));
        return failure;
      }

      final String finalAnswer = result.getContent();
      if (StringUtils.isNotBlank(finalAnswer)) {
        invokeListeners(listener -> listener.onFinalAnswer(sessionId, result));
        finalResponse = result;
        break;
      }

      final List<String> toolRequests = result.getToolRequests();
      if (CollectionUtils.isEmpty(toolRequests)) {
        sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.system(MISSING_TOOL_AND_FINAL_MESSAGE));
        continue;
      }
      executeToolRequests(sessionId, toolRequests);
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

    final StringBuilder sb = new StringBuilder();
    if (StringUtils.isNotBlank(response.getContent())) {
      sb.append(response.getContent()).append("\n\n");
    }
    if (CollectionUtils.isNotEmpty(response.getToolRequests())) {
      sb.append("TOOL REQUESTS").append("\n");
      response.getToolRequests().forEach(request -> {
        sb.append("* ").append(request).append("\n");
      });
    }
    sessionStore.appendMessage(getReasoningSessionId(sessionId), new Message(response, sb.toString()));

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

  private List<ToolCall> runToolAssistant(final String sessionId, final List<ToolRequest> toolRequests) {
    final String message = TemplateUtils.renderTemplateForName("hybrid/tool_assistant_json.txt", Map.of("toolRequests",
        JsonUtils.toJson(toolRequests), "tool_schema", loadResourceAsString("/schemas/hybrid/tool_call_schema.json")));
    sessionStore.appendMessage(getToolSessionId(sessionId), Message.user(message));

    final Map<String, ToolCall> matchedCallsById = new HashMap<>();
    List<ToolRequest> remainingRequests = new ArrayList<>(toolRequests);
    int repairAttempts = 0;

    do {
      final List<Message> prompt = toolAssistantContextBuilder.buildPrompt(getToolSessionId(sessionId));
      Message response = toolAssistantModel.generate(prompt);
      final Message sanitized = EngineUtils.sanitizeMessage(response, toolAssistantModel.responseFormat(),
          toolAssistantModel.thoughtsEnabled(), toolAssistantModel.thoughtsStartTag(),
          toolAssistantModel.thoughtsEndTag());
      final List<ToolCall> toolCalls = CollectionUtils.nullSafeList(sanitized.getToolCalls());
      sessionStore.appendMessage(getToolSessionId(sessionId), sanitized);
      final Map<String, ToolCall> newlyMatched = selectMatchingToolCalls(toolCalls, remainingRequests);
      newlyMatched.forEach(matchedCallsById::putIfAbsent);
      remainingRequests = unresolvedRequests(toolRequests, matchedCallsById);
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
    } while (CollectionUtils.isNotEmpty(remainingRequests));

    return buildOrderedToolCalls(toolRequests, matchedCallsById);
  }

  private void executeToolRequests(final String sessionId, final List<String> toolRequests) {
    if (CollectionUtils.isEmpty(toolRequests)) {
      return;
    }
    // TODO: check later on whether reasoning response should be added to tool
    // assistant context
    // sessionStore.appendMessage(getToolSessionId(sessionId),
    // cloneMessage(reasoningMessage));
    executeToolPlan(sessionId, toolRequests);
  }

  private void executeToolPlan(final String sessionId, final List<String> toolRequests) {
    // Process initial requests
    final List<ToolCall> toolCalls = runToolAssistant(sessionId, EngineUtils.parseToolRequestInfo(toolRequests));
    if (CollectionUtils.isEmpty(toolCalls)) {
      sessionStore.appendMessage(getReasoningSessionId(sessionId),
          Message.system("Unable to execute tools. Please try alternate approach"));
      return;
    }

    invokeListeners(listener -> listener.onToolPlan(sessionId, toolCalls));

    final List<ToolExecution> executions = _executeTools(toolCalls);
    emitToolExecutionEvents(sessionId, executions);
    final Map<String, ToolExecution> toolCallIdVsResult = CollectionUtils.transformToMap(executions,
        execution -> execution.getToolCall().id(), Function.identity());
    sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.user(
        buildToolResultMessage(toolCalls.stream().map(toolCall -> toolCallIdVsResult.get(toolCall.id())).toList())));
  }

  /**
   * Emits tool execution events for known tools (excluding unknown tools)
   */
  private void emitToolExecutionEvents(String sessionId, List<ToolExecution> executions) {
    for (ToolExecution toolExecution : executions) {
      // Skip execution events for unknown tools (identified by specific status)
      if (!"unknown".equals(toolExecution.getStatus())) {
        invokeListeners(listener -> listener.onToolExecution(sessionId, toolExecution));
      }
    }
  }

  private void appendUserMessage(final String sessionId, final Message message) {
    sessionStore.appendMessage(sessionId, Message.user(message.getContent()));
  }

  private static String buildToolResultMessage(final List<ToolExecution> executions) {
    List<Map<String, Object>> results = new ArrayList<>();
    for (ToolExecution execution : executions) {
      ToolCall call = execution.getToolCall();
      Map<String, Object> entry = new HashMap<>();
      entry.put("id", call == null ? null : call.id());
      entry.put("name", call == null ? null : call.name());
      entry.put("args", call == null ? null : call.args());
      entry.put("status", execution.getStatus());
      entry.put("output", execution.getOutput());
      entry.put("duration_ms", execution.getDurationMs());
      results.add(entry);
    }
    Map<String, Object> payload = Map.of("tool_calls", results);
    return JsonUtils.toJson(payload);
  }

  private List<ToolExecution> _executeTools(final Collection<ToolCall> toolCalls) {
    final List<ToolExecution> executions = new ArrayList<>();
    for (ToolCall call : toolCalls) {
      final Tool tool = toolByName.get(call.name());
      final Instant start = Instant.now();
      String status = "ok";
      String output;
      if (tool == null) {
        status = "unknown";  // Specific status for unknown tools
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
      toolExecution.setId(UUID.randomUUID().toString().replaceAll("-", ""));

      executions.add(toolExecution);
    }
    return executions;
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

  // returns tool calls in order of requests, skipping unmatched requests
  private static List<ToolCall> buildOrderedToolCalls(final List<ToolRequest> toolRequests,
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
