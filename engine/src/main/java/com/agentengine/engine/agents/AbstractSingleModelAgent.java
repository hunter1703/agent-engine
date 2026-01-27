package com.agentengine.engine.agents;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.tools.ToolExecutor;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSingleModelAgent implements Agent {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractSingleModelAgent.class);

  private final String name;
  private final String description;
  private final LLMModel model;
  private final ToolExecutor toolExecutor;

  protected AbstractSingleModelAgent(final String name, final String description, final LLMModel model) {
    this(name, description, model, null);
  }

  protected AbstractSingleModelAgent(final String name, final String description, final LLMModel model,
      final ToolExecutor toolExecutor) {
    this.name = name;
    this.description = description;
    this.model = model;
    this.toolExecutor = toolExecutor;
  }

  protected final LLMModel model() {
    return model;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public Message invoke(final String sessionId, final Message message, final AgentListener listener) {
    final String runId = UUID.randomUUID().toString();
    try {
      appendUserMessage(sessionId, runId, message);
      listener.onRunStarted(sessionId, runId);

      Message response = runModel(sessionId, runId, listener);

      // Handle tool calls if a tool executor is available
      if (toolExecutor != null && response != null && CollectionUtils.isNotEmpty(response.getToolCalls())) {
        // Execute the tool calls
        List<ToolExecution> executions = toolExecutor.execute(sessionId, runId, response.getToolCalls(), listener);

        // Append the tool results to the context
        appendToolResults(sessionId, runId, executions);

        // Generate a final response based on the tool results
        response = runModel(sessionId, runId, listener);
      }

      listener.onRunFinished(sessionId, runId);
      return response;
    } catch (Exception e) {
      LOG.error("Error during agent invocation", e);
      listener.onRunError(sessionId, runId, e);
      throw e;
    }
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return model.getContextManager().buildPrompt(sessionId);
  }

  private Message runModel(final String sessionId, final String runId, final AgentListener listener) {
    try {
      final List<Message> prompt = CollectionUtils.nullSafeMutableList(buildPrompt(sessionId));
      final Message response = model.generate(prompt);
      model.getContextManager().appendMessage(sessionId, runId, response);
      emitAssistantMessage(sessionId, response, listener);
      return response;
    } catch (Exception e) {
      LOG.error("Error during model generation", e);
      throw e;
    }
  }

  private void appendUserMessage(final String sessionId, final String runId, final Message message) {
    if (message == null) {
      return;
    }
    model.getContextManager().appendMessage(sessionId, runId, Message.user(message.getContent()));
  }

  private void appendToolResults(final String sessionId, final String runId, final List<ToolExecution> executions) {
    if (CollectionUtils.isEmpty(executions)) {
      return;
    }
    model.getContextManager().appendMessage(sessionId, runId, Message.user(formatToolExecutions(executions)));
  }

  private String formatToolExecutions(final List<ToolExecution> executions) {
    if (CollectionUtils.isEmpty(executions)) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < executions.size(); i++) {
      ToolExecution execution = executions.get(i);
      if (i > 0) {
        sb.append("\n");
      }
      sb.append("Tool ").append(execution.getToolCall().name()).append(" result: ").append(execution.getOutput());
    }
    return sb.toString();
  }

  private void emitAssistantMessage(final String sessionId, final Message message, final AgentListener listener) {
    if (message == null) {
      return;
    }
    final String id = message.getId();
    listener.onTextMessageStart(sessionId, id, "assistant");
    if (StringUtils.isNotBlank(message.getContent())) {
      listener.onTextMessageDelta(sessionId, id, message.getContent());
    }
    listener.onTextMessageEnd(sessionId, id);

    // Emit tool call events if there are any
    if (CollectionUtils.isNotEmpty(message.getToolCalls())) {
      for (ToolCall toolCall : message.getToolCalls()) {
        listener.onToolCallStart(sessionId, toolCall.id(), toolCall.name());
        if (toolCall.args() != null) {
          listener.onToolCallArgs(sessionId, toolCall.id(), toolCall.args().toString());
        }
        listener.onToolCallEnd(sessionId, toolCall.id());
      }
    }
  }
}
