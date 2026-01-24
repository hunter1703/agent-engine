package com.agentengine.engine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolAdapterTest {

  @Test
  void executeDelegatesToInvoke() {
    RecordingAgent agent = new RecordingAgent();

    ToolResult result = agent.executeWithContext(new ToolContext("session"), Map.of("message", "hello"));

    assertThat(result.output()).isEqualTo("response");
    assertThat(agent.lastMessage).isEqualTo("hello");
    assertThat(agent.lastSessionId).isEqualTo("session");
  }

  private static final class RecordingAgent implements Agent {
    private String lastSessionId;
    private String lastMessage;

    @Override
    public Message invoke(final String sessionId, final Message message, final AgentListener listener) {
      lastSessionId = sessionId;
      lastMessage = message == null ? null : message.getContent();
      Message response = new Message(Role.ASSISTANT, "response", null, null);
      listener.onTextMessageStart(sessionId, "msg-1", "assistant");
      listener.onTextMessageDelta(sessionId, "msg-1", response.getContent());
      listener.onTextMessageEnd(sessionId, "msg-1");
      return response;
    }

    @Override
    public List<Message> buildPrompt(final String sessionId) {
      return List.of();
    }
  }
}
