package com.agentengine.engine.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.StateStore;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.Tool;
import java.util.ArrayList;
import java.util.List;

public class BaseContextManager implements ContextManager {
  protected final StateStore stateStore;
  private final Message systemMessage;

  public BaseContextManager(final StateStore stateStore, final String systemMessage, final String protocolMessage, final List<Tool> tools) {
    this.stateStore = stateStore;
    this.systemMessage = Message.system(STR."""
    # YOUR MANDATE :\s
    \{systemMessage}
    ---

    # PROTOCOL YOU MUST FOLLOW
    \{protocolMessage}

    ---

    # TOOLS
    \{buildToolMessage(tools)}
    """);
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return buildPrompt(stateStore.getMessages(sessionId));
  }

  @Override
  public String appendMessage(final String sessionId, final String runId, final Message message) {
    return stateStore.appendMessage(sessionId, runId, message);
  }

  protected List<Message> buildPrompt(final List<Message> messages) {
    final List<Message> prompt = new ArrayList<>();
    prompt.add(systemMessage);
    prompt.addAll(CollectionUtils.nullSafeList(messages));

    return prompt;
  }

  private String buildToolMessage(final List<Tool> tools) {
    if (tools == null || tools.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder("<AVAILABLE_TOOLS>\n");
    for (Tool tool : tools) {
      String line = STR."- \{tool.name()}";
      if (tool.description() != null && !tool.description().isBlank()) {
        line += STR." - \{tool.description()}";
      }
      builder.append(line).append("\n");
    }
    builder.append("</AVAILABLE_TOOLS>");
    return builder.toString();
  }
}
