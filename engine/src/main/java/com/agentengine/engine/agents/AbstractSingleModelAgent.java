package com.agentengine.engine.agents;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.utils.EngineUtils;
import java.util.List;
import java.util.UUID;

public abstract class AbstractSingleModelAgent implements Agent {
  private final String name;
  private final String description;
  private final LLMModel model;

  protected AbstractSingleModelAgent(final String name, final String description, final LLMModel model) {
    this.name = name;
    this.description = description;
    this.model = model;
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
    appendUserMessage(sessionId, runId, message);
    listener.onRunStarted(sessionId, runId);
    final Message response = runModel(sessionId, runId, listener);
    listener.onRunFinished(sessionId, runId);
    return response;
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return model.getContextManager().buildPrompt(sessionId);
  }

  private Message runModel(final String sessionId, final String runId, final AgentListener listener) {
    final List<Message> prompt = CollectionUtils.nullSafeMutableList(buildPrompt(sessionId));
    final Message response = model.generate(prompt);
    final Message sanitized = EngineUtils.sanitizeMessage(response, model.responseFormat(), model.thoughtsEnabled(),
        model.thoughtsStartTag(), model.thoughtsEndTag());
    model.getContextManager().appendMessage(sessionId, runId, sanitized);
    emitAssistantMessage(sessionId, sanitized, listener);
    return sanitized;
  }

  private void appendUserMessage(final String sessionId, final String runId, final Message message) {
    if (message == null) {
      return;
    }
    model.getContextManager().appendMessage(sessionId, runId, Message.user(message.getContent()));
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
  }
}
