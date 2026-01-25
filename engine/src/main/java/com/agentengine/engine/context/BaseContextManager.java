package com.agentengine.engine.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.MessageStoreMark;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolUtils;
import java.util.ArrayList;
import java.util.List;

public class BaseContextManager implements ContextManager {
  protected final String role;
  protected MessageStore messageStore;
  private final Message systemMessage;

  public BaseContextManager(final String role, final MessageStore messageStore, final String systemMessage, final String protocolMessage, final List<Tool> tools) {
    this.role = role;
    this.messageStore = messageStore;
      this.systemMessage = Message.system(STR."""
    # YOUR MANDATE :\s
    \{systemMessage}
    ---

    # PROTOCOL YOU MUST FOLLOW
    \{protocolMessage}

    ---

    # TOOLS
    \{ToolUtils.buildToolMessage(tools)}
    """);
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return buildPrompt(messageStore.getMessages(sessionId, role));
  }

  @Override
  public String appendMessage(final String sessionId, final String runId, final Message message) {
    message.setRunId(runId);
    return messageStore.appendMessage(sessionId, role, message);
  }

  @Override
  public MessageStoreMark mark(final String sessionId) {
    return messageStore.mark(sessionId, role);
  }

  @Override
  public void reset(final String sessionId, final MessageStoreMark mark) {
    messageStore.reset(sessionId, role, mark);
  }

  protected List<Message> buildPrompt(final List<Message> messages) {
    final List<Message> prompt = new ArrayList<>();
    prompt.add(systemMessage);
    prompt.addAll(CollectionUtils.nullSafeList(messages));

    return prompt;
  }

}
