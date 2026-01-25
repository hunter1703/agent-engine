package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.tools.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseContextManagerTest {

  @Test
  void buildPromptIncludesSystemProtocolAndTools() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    sessionStore.appendMessage("test-agent", "session", "run", Message.user("hi"));

    List<Tool> tools = List.of(new StubTool("calc", "calculator"), new StubTool("echo", null));
    BaseContextManager builder = new BaseContextManager(sessionStore, "system", "protocol", tools);

    List<Message> prompt = builder.buildPrompt("session");

    assertThat(prompt).hasSize(2);
    assertThat(prompt.getFirst().getRole()).isEqualTo(Role.SYSTEM);
    assertThat(prompt.getFirst().getContent()).contains("protocol");
    assertThat(prompt.getFirst().getContent()).contains("system");
    assertThat(prompt.getFirst().getContent()).contains("<AVAILABLE_TOOLS>");
  }

  @Test
  void buildPromptOmitsToolBlockWhenNoTools() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    sessionStore.appendMessage("test-agent", "session", "run", Message.user("hi"));

    BaseContextManager builder = new BaseContextManager(sessionStore, "system", "protocol", List.of());

    List<Message> prompt = builder.buildPrompt("session");

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
