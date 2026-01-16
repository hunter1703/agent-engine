package com.localagent.engine.context;

import com.localagent.engine.message.Message;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.utils.CollectionUtils;
import java.util.ArrayList;
import java.util.List;

public class BaseContextBuilder implements ContextBuilder {
  protected final SessionStore sessionStore;
  private final Message systemMessage;
  private final Message protocolMessage;
  private final Message toolMessage;

  public BaseContextBuilder(
      final SessionStore sessionStore,
      final String systemMessage,
      final String protocolMessage,
      final List<AgentTool> tools) {
    this.sessionStore = sessionStore;
    this.systemMessage = Message.system(systemMessage);
    this.protocolMessage = Message.system(protocolMessage);
    this.toolMessage = buildToolMessage(tools);
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return buildPrompt(sessionStore.getMessages(sessionId));
  }

  protected List<Message> buildPrompt(final List<Message> messages) {
    final List<Message> prompt = new ArrayList<>();
    if (protocolMessage != null) {
      prompt.add(protocolMessage);
    }
    if (toolMessage != null) {
      prompt.add(toolMessage);
    }
    prompt.add(systemMessage);
    prompt.addAll(CollectionUtils.nullSafeList(messages));

    return prompt;
  }

  private Message buildToolMessage(final List<AgentTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return null;
    }
    final StringBuilder builder = new StringBuilder("<AVAILABLE_TOOLS>\n");
    for (AgentTool tool : tools) {
      String line = STR."- \{tool.name()}";
      if (tool.description() != null && !tool.description().isBlank()) {
        line += STR." - \{tool.description()}";
      }
      builder.append(line).append("\n");
    }
    builder.append("</AVAILABLE_TOOLS>");
    return Message.system(builder.toString());
  }
}
