package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.state.InMemoryMessageStore;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseContextManagerTest {

  @Test
  void buildPromptIncludesSystemProtocolAndTools() {
    final InMemoryMessageStore messageStore = new InMemoryMessageStore();
    final String sessionId = "session";
    final String role = "reasoning";
    messageStore.appendMessage(sessionId, role, Message.user("hi"));

    final List<Tool> tools = List.of(new StubTool("calc", "calculator"), new StubTool("echo", null));
    final BaseContextManager builder = new BaseContextManager(role, messageStore, "system", "protocol", tools);

    final List<Message> prompt = builder.buildPrompt(sessionId);

    assertThat(prompt).hasSize(2);
    assertThat(prompt.getFirst().getRole()).isEqualTo(Role.SYSTEM);
    assertThat(prompt.getFirst().getContent()).contains("protocol");
    assertThat(prompt.getFirst().getContent()).contains("system");
    assertThat(prompt.getFirst().getContent()).contains("<AVAILABLE_TOOLS>");
  }

  @Test
  void buildPromptOmitsToolBlockWhenNoTools() {
    final InMemoryMessageStore messageStore = new InMemoryMessageStore();
    final String sessionId = "session";
    final String role = "reasoning";
    messageStore.appendMessage(sessionId, role, Message.user("hi"));

    final BaseContextManager builder = new BaseContextManager(role, messageStore, "system", "protocol", List.of());

    final List<Message> prompt = builder.buildPrompt(sessionId);

    assertThat(prompt).hasSize(2);
    assertThat(prompt.getFirst().getContent()).doesNotContain("<AVAILABLE_TOOLS>");
    assertThat(prompt.get(1).getRole()).isEqualTo(Role.USER);
  }

  private record StubTool(String name, String description) implements Tool {

    @Override
    public String execute(Map<String, Object> args) {
      return "ok";
    }
  }
}
