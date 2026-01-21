package com.agentengine.engine;

import static com.agentengine.commons.utils.ResourceUtils.loadResourceAsString;
import static java.lang.StringTemplate.STR;

import com.agentengine.commons.utils.CollectionUtils;
import com.agentengine.commons.utils.JsonUtils;
import com.agentengine.commons.utils.StringUtils;
import com.agentengine.commons.utils.TemplateUtils;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.exception.AgentException;
import com.agentengine.engine.api.exception.ModelInvocationException;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.beans.session.ToolRequest;
import com.agentengine.engine.api.state.SessionStore;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.tools.AgenticToolExecutor;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.utils.EngineUtils;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HybridAgent implements Agent {
  private static final String REASONING_SESSION_SUFFIX = "_reasoning";
  private static final String TOOL_SESSION_SUFFIX = "_reasoning";
  private static final String MISSING_TOOL_AND_FINAL_MESSAGE = "You must provide at least a final answer or tool requests as defined in protocol.";
  private final LLMModel reasoningModel;
  private final AgenticToolExecutor toolExecutor;
  private final ContextBuilder reasoningContextBuilder;
  private final SessionStore sessionStore;
  private final int invocationLimit;

  public HybridAgent(final LLMModel reasoningModel, final AgenticToolExecutor toolExecutor,
      final ContextBuilder reasoningContextBuilder, final SessionStore sessionStore, final int invocationLimit) {
    this.reasoningModel = reasoningModel;
    this.toolExecutor = toolExecutor;
    this.reasoningContextBuilder = reasoningContextBuilder;
    this.sessionStore = sessionStore;
    this.invocationLimit = Math.max(1, invocationLimit);
  }

  @Override
  public Message invoke(final String sessionId, final Message message, final AgentListener listener) {
    listener.onStart(sessionId);
    appendUserMessage(getReasoningSessionId(sessionId), message);
    appendUserMessage(getToolSessionId(sessionId), message);
    Message finalResponse = null;
    do {
      final Message result;
      try {
        result = runReasoner(sessionId, listener);
      } catch (AgentException ex) {
        listener.onError(sessionId, ex);
        throw ex;
      } catch (Exception ex) {
        listener.onError(sessionId, ex);
        final Message failure = Message.system(STR."Reasoner failed: \{ex.getMessage()}");
        listener.onFinalAnswer(sessionId, failure);
        return failure;
      }

      final String finalAnswer = result.getContent();
      if (StringUtils.isNotBlank(finalAnswer)) {
        listener.onFinalAnswer(sessionId, result);
        finalResponse = result;
        break;
      }

      final List<String> toolRequests = result.getToolRequests();
      if (CollectionUtils.isEmpty(toolRequests)) {
        sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.system(MISSING_TOOL_AND_FINAL_MESSAGE));
        continue;
      }
      executeToolRequests(sessionId, toolRequests, listener);
    } while (EngineUtils.invocationsThisTurn(sessionStore, getReasoningSessionId(sessionId))
        < invocationLimit);

    if (finalResponse != null) {
      listener.onEnd(sessionId);
      return finalResponse;
    }
    final Message invocationsExceededMessage =
        Message.system(STR."Number of assistant invocations exceeded maximum : \{invocationLimit}");
    listener.onFinalAnswer(sessionId, invocationsExceededMessage);
    return invocationsExceededMessage;
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return reasoningContextBuilder.buildPrompt(getReasoningSessionId(sessionId));
  }

  private Message runReasoner(final String sessionId, final AgentListener listener) {
    listener.onReasoningStart(sessionId);
    Message message = null;
    try {
      message = _runReasoner(sessionId, 5);
    } finally {
      listener.onReasoningEnd(sessionId, message);
    }
    return message;
  }

  private Message _runReasoner(final String sessionId, final int maxRetries) {
    final List<Message> prompt = CollectionUtils
        .nullSafeMutableList(reasoningContextBuilder.buildPrompt(getReasoningSessionId(sessionId)));
    Message response;
    try {
      response = reasoningModel.generate(prompt);
    } catch (Exception e) {
      throw new ModelInvocationException("reasoning-model", "Failed to generate reasoning response", e);
    }
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
    return sessionId + REASONING_SESSION_SUFFIX;
  }

  private static String getToolSessionId(final String sessionId) {
    return sessionId + TOOL_SESSION_SUFFIX;
  }

  private void executeToolRequests(final String sessionId, final List<String> toolRequests,
      final AgentListener listener) {
    List<ToolExecution> executions = toolExecutor.executeRequests(sessionId, toolRequests, listener);

    if (CollectionUtils.isEmpty(executions)) {
      // Executor failed to map requests to tools or execution failed silently
      sessionStore.appendMessage(getReasoningSessionId(sessionId),
          Message.system("Unable to execute tools. Please try alternate approach"));
      return;
    }

    final Map<String, ToolExecution> toolCallIdVsResult = CollectionUtils.transformToMap(executions,
        execution -> execution.getToolCall().id(), Function.identity());

    // Append tool results to reasoning session history
    sessionStore.appendMessage(getReasoningSessionId(sessionId), Message.user(
        buildToolResultMessage(executions.stream()
            .map(ToolExecution::getToolCall)
            .filter(Objects::nonNull)
            .map(call -> toolCallIdVsResult.get(call.id()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList()))));
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

  private void appendUserMessage(final String sessionId, final Message message) {
    sessionStore.appendMessage(sessionId, Message.user(message.getContent()));
  }
}
