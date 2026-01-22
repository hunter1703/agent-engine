package com.agentengine.engine.tools;

import static com.agentengine.commons.utils.ResourceUtils.loadResourceAsString;
import static java.lang.StringTemplate.STR;

import com.agentengine.commons.utils.CollectionUtils;
import com.agentengine.commons.utils.JsonUtils;
import com.agentengine.commons.utils.StringUtils;
import com.agentengine.commons.utils.TemplateUtils;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.beans.session.ToolRequest;
import com.agentengine.engine.api.exception.ModelInvocationException;
import com.agentengine.engine.api.state.SessionStore;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.utils.EngineUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public class ToolRequestExecutor implements AgenticToolExecutor {

  private static final String TOOL_SESSION_SUFFIX = "_tool";

  private final LLMModel toolAssistantModel;
  private final ContextBuilder toolAssistantContextBuilder;
  private final Map<String, Tool> toolByName;
  private final SessionStore sessionStore;

  public ToolRequestExecutor(final LLMModel toolAssistantModel, final ContextBuilder toolAssistantContextBuilder,
      final Map<String, Tool> toolByName, final SessionStore sessionStore) {
    this.toolAssistantModel = toolAssistantModel;
    this.toolAssistantContextBuilder = toolAssistantContextBuilder;
    this.toolByName = toolByName;
    this.sessionStore = sessionStore;
  }

  @Override
  public List<ToolExecution> executeRequests(final String sessionId, final List<String> toolRequests,
      final AgentListener listener) {
    if (CollectionUtils.isEmpty(toolRequests)) {
      return Collections.emptyList();
    }

    // Sync step: strictly handle turn-specific instructions.
    // Seeding of USER intent is now handled by the Agent.
    syncToolSession(sessionId, toolRequests);

    return executeToolPlan(sessionId, toolRequests, listener);
  }

  @Override
  public List<ToolExecution> execute(final String sessionId, final List<ToolCall> toolCalls,
      final AgentListener listener) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      // Caller is responsible for handling empty execution plan
      return Collections.emptyList();
    }

    listener.onToolPlan(sessionId, toolCalls);

    final List<ToolExecution> executions = _executeTools(sessionId, toolCalls, listener);

    return executions;
  }

  private void syncToolSession(final String sessionId, final List<String> toolRequests) {
    final String toolSessionId = getToolSessionId(sessionId);

    // Append the turn-specific execution instruction.
    // This keeps the tool assistant focused on the current requests.
    final String toolCallSchema = loadResourceAsString("/schemas/hybrid/tool_call_schema.json");
    final String initialInstruction = TemplateUtils.renderTemplateForName("hybrid/tool_assistant_json.txt",
        Map.of("toolRequests", JsonUtils.toJson(toolRequests), "tool_schema", toolCallSchema));
    sessionStore.appendMessage(toolSessionId, Message.user(initialInstruction));
  }

  private List<ToolExecution> executeToolPlan(final String sessionId, final List<String> toolRequests,
      final AgentListener listener) {
    // Process initial requests
    final List<ToolCall> toolCalls = runToolAssistant(sessionId, EngineUtils.parseToolRequestInfo(toolRequests),
        listener);
    return execute(sessionId, toolCalls, listener);
  }

  private List<ToolCall> runToolAssistant(final String sessionId, final List<ToolRequest> toolRequests,
      final AgentListener listener) {
    final Map<String, ToolCall> matchedCallsById = new HashMap<>();
    List<ToolRequest> remainingRequests = new ArrayList<>(toolRequests);
    int repairAttempts = 0;

    do {
      final List<Message> prompt = toolAssistantContextBuilder.buildPrompt(getToolSessionId(sessionId));
      Message response;
      try {
        response = toolAssistantModel.generate(prompt);
      } catch (Exception e) {
        throw new ModelInvocationException("tool-assistant-model", "Failed to generate tool assistant response", e);
      }
      final Message sanitized = EngineUtils.sanitizeMessage(response, toolAssistantModel.responseFormat(),
          toolAssistantModel.thoughtsEnabled(), toolAssistantModel.thoughtsStartTag(),
          toolAssistantModel.thoughtsEndTag());
      List<ToolCall> toolCalls = CollectionUtils.nullSafeList(sanitized.getToolCalls());
      if (toolCalls.isEmpty() && StringUtils.isNotBlank(sanitized.getContent())) {
        final Message parsed = EngineUtils.parseJsonPayload(sanitized.getContent());
        if (parsed != null) {
          toolCalls = CollectionUtils.nullSafeList(parsed.getToolCalls());
        }
      }
      sessionStore.appendMessage(getToolSessionId(sessionId), sanitized);

      final Map<String, ToolCall> newlyMatched = selectMatchingToolCalls(toolCalls, remainingRequests);
      newlyMatched.forEach(matchedCallsById::putIfAbsent);
      remainingRequests = unresolvedRequests(toolRequests, matchedCallsById);
      if (CollectionUtils.isNotEmpty(remainingRequests)) {
        final List<ToolCall> newToolCalls = CollectionUtils.nullSafeList(toolCalls);
        final List<ToolRequest> newRemainingRequests = CollectionUtils.nullSafeList(remainingRequests);
        listener.onToolRepair(sessionId, newToolCalls, newRemainingRequests);
        final List<String> missingRequests = remainingRequests.stream().map(ToolRequest::raw).toList();
        sessionStore.appendMessage(getToolSessionId(sessionId), Message.user(TemplateUtils
            .renderTemplateForName("hybrid/repair/empty_tool_call.txt", Map.of("toolRequests", missingRequests))));
        repairAttempts++;
        if (repairAttempts > 3) {
          // Caller responsible for handling failure
          break;
        }
      }
    } while (CollectionUtils.isNotEmpty(remainingRequests));

    return buildOrderedToolCalls(toolRequests, matchedCallsById);
  }

  private List<ToolExecution> _executeTools(final String sessionId, final Collection<ToolCall> toolCalls, final AgentListener listener) {
    final List<ToolExecution> executions = new ArrayList<>();
    for (ToolCall call : toolCalls) {
      listener.onToolCallStart(sessionId, call.id(), call.name());
      if (call.args() != null) {
        listener.onToolCallArgs(sessionId, call.id(), JsonUtils.toJson(call.args()));
      }
      listener.onToolCallEnd(sessionId, call.id());

      final Tool tool = toolByName.get(call.name());
      final Instant start = Instant.now();
      String status = "ok";
      String output;
      if (tool == null) {
        status = "unknown";  // Specific status for unknown tools
        output = STR."Unknown tool: \{call.name()}";
      } else {
        try {
          final ToolContext context = new ToolContext(sessionId);
          final ToolResult result = tool.executeWithContext(context, call.args());
          output = result.output();
          status = result.status();
        } catch (Exception ex) {
          status = "error";
          output = STR."Tool error in \{call.name()}: \{ex.getMessage()}";
        }
      }
      listener.onToolCallResult(sessionId, call.id(), output);

      final Instant end = Instant.now();
      final ToolExecution toolExecution =
          new ToolExecution(call, status, output, start, end.toEpochMilli() - start.toEpochMilli());
      toolExecution.setId(UUID.randomUUID().toString().replaceAll("-", ""));

      executions.add(toolExecution);
    }
    return executions;
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

  private static String getToolSessionId(final String sessionId) {
    return sessionId + TOOL_SESSION_SUFFIX;
  }
}
