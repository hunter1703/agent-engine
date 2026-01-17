package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.Role;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.tools.AgentTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseContextBuilderTest {

  @Test
  void buildPromptIncludesProtocolToolsSystemAndMessages() {
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    sessionStore.appendMessage("session", Message.user("hi"));

    List<AgentTool> tools = List.of(new StubTool("calc", "calculator"), new StubTool("echo", null));
    BaseContextBuilder builder = new BaseContextBuilder(sessionStore, "system", "protocol", tools);

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
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    sessionStore.appendMessage("session", Message.user("hi"));

    BaseContextBuilder builder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());

    List<Message> prompt = builder.buildPrompt("session");

    assertThat(prompt).hasSize(3);
    assertThat(prompt.get(0).getContent()).isEqualTo("protocol");
    assertThat(prompt.get(1).getContent()).isEqualTo("system");
    assertThat(prompt.get(2).getRole()).isEqualTo(Role.USER);
  }

  private record StubTool(String name, String description) implements AgentTool {

    @Override
    public String execute(Map<String, Object> args) {
      return "ok";
    }
  }
}
