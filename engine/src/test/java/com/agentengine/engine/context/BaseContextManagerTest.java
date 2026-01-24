package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.state.InMemoryStateStore;
import com.agentengine.engine.tools.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseContextManagerTest {

  @Test
  void buildPromptIncludesProtocolToolsSystemAndMessages() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    sessionStore.appendMessage("session", "run", Message.user("hi"));

    List<Tool> tools = List.of(new StubTool("calc", "calculator"), new StubTool("echo", null));
    BaseContextManager builder = new BaseContextManager(sessionStore, "system", "protocol", tools);

    List<Message> prompt = builder.buildPrompt("session");

    assertThat(prompt).hasSize(4);
    assertThat(prompt.get(0).getContent()).isEqualTo("protocol");
    assertThat(prompt.get(0).getRole()).isEqualTo(Role.SYSTEM);
    assertThat(prompt.get(1).getContent()).contains("<AVAILABLE_TOOLS>");
    assertThat(prompt.get(1).getContent()).contains("calc");
    assertThat(prompt.get(2).getContent()).isEqualTo("system");
    assertThat(prompt.get(3).getRole()).isEqualTo(Role.USER);
  }

  @Test
  void buildPromptSkipsToolBlockWhenNoToolsProvided() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    sessionStore.appendMessage("session", "run", Message.user("hi"));

    BaseContextManager builder = new BaseContextManager(sessionStore, "system", "protocol", List.of());

    List<Message> prompt = builder.buildPrompt("session");

    assertThat(prompt).hasSize(3);
    assertThat(prompt.get(0).getContent()).isEqualTo("protocol");
    assertThat(prompt.get(1).getContent()).isEqualTo("system");
    assertThat(prompt.get(2).getRole()).isEqualTo(Role.USER);
  }

  private record StubTool(String name, String description) implements Tool {

    @Override
    public String execute(Map<String, Object> args) {
      return "ok";
    }
  }
}
