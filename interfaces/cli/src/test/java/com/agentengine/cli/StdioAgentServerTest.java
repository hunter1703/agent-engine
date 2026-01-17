package com.agentengine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.cli.beans.BuildPromptRequest;
import com.agentengine.cli.beans.InvokeAgentRequest;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.AgentListener;
import com.agentengine.engine.events.AgentEventAdapter;
import com.agentengine.engine.events.AgentEventPublisher;
import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.EngineConfig;
import com.agentengine.engine.builders.AgentBuilder;
import com.agentengine.engine.builders.AgentBuilderFactory;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.Role;
import com.agentengine.engine.message.ToolCall;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StdioAgentServerTest {
  private PrintStream originalOut;
  private java.io.InputStream originalIn;

  @BeforeEach
  void setup() {
    originalOut = System.out;
    originalIn = System.in;
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setIn(originalIn);
  }

  @Test
  void invokeEmitsFinalAnswerEvent() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

    StdioAgentServer server = new StdioAgentServer(null, null);
    setField(server, "agent", new FakeAgent());
    setField(server, "sessionId", "session");

    InvokeAgentRequest request = new InvokeAgentRequest();
    request.setId("req-1");
    request.setUserMessage("hello");

    server.invoke(request);

    String printed = output.toString(StandardCharsets.UTF_8);
    assertThat(printed).contains("\"event\":\"finalAnswer\"");
    assertThat(printed).contains("\"text\":\"done\"");
    assertThat(printed).doesNotContain("thoughts");
  }

  @Test
  void buildPromptEmitsMessageList() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

    StdioAgentServer server = new StdioAgentServer(null, null);
    setField(server, "agent", new FakeAgent());
    setField(server, "sessionId", "session");

    BuildPromptRequest request = new BuildPromptRequestTest();
    request.setId("req-2");

    server.buildPrompt(request);

    String printed = output.toString(StandardCharsets.UTF_8);
    assertThat(printed).contains("\"role\":\"system\"");
    assertThat(printed).contains("\"content\":\"sys\"");
  }

  @Test
  void runProcessesInvokeAndBuildPromptRequests() throws Exception {
    com.alibaba.fastjson2.JSONFactory.getDefaultObjectReaderProvider()
        .addAutoTypeAccept("com.agentengine.cli.beans.");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    FakeAgent engine = new FakeAgent();
    when(builder.build(anyString(), any())).thenReturn(engine);
    when(builderFactory.getBuilder(anyString())).thenReturn(builder);

    com.agentengine.engine.beans.config.ConfigLoader configLoader =
        mock(com.agentengine.engine.beans.config.ConfigLoader.class);
    when(configLoader.loadConfig(any())).thenReturn(AgentConfig.empty());

    StdioAgentServer server = new StdioAgentServer(builderFactory, configLoader);

    String invokeJson =
        "{"
            + "\"@type\":\"com.agentengine.cli.beans.InvokeAgentRequest\","
            + "\"id\":\"req-1\","
            + "\"user_message\":\"hello\"}";
    String promptJson =
        "{" + "\"@type\":\"com.agentengine.cli.beans.BuildPromptRequest\"," + "\"id\":\"req-2\"}";
    String input = invokeJson + "\n" + promptJson + "\n";
    System.setIn(new java.io.ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

    int result = server.run("app", "agent", "config.json");

    assertThat(result).isEqualTo(0);
    String printed = output.toString(StandardCharsets.UTF_8);
    assertThat(printed).contains("\"event\":\"finalAnswer\"");
    assertThat(printed).contains("\"messages\"");
  }

  @Test
  void initRegistersListenerAndEmitsToolEvents() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    FakeAgent engine = new FakeAgent();
    when(builder.build(anyString(), any())).thenReturn(engine);
    when(builderFactory.getBuilder(anyString())).thenReturn(builder);

    com.agentengine.engine.beans.config.ConfigLoader configLoader =
        mock(com.agentengine.engine.beans.config.ConfigLoader.class);
    AgentConfig config = AgentConfig.empty();
    EngineConfig engineConfig = config.getEngine();
    engineConfig.setReasoning("reasoning.json");
    engineConfig.setPrompt("prompt");
    when(configLoader.loadConfig(any())).thenReturn(config);

    StdioAgentServer server = new StdioAgentServer(builderFactory, configLoader);
    invokeInit(server, "agent", Path.of("config.json").toString());

    AgentEventPublisher publisher = new TestEventPublisher(server);
    AgentEventAdapter adapter = new AgentEventAdapter(publisher);
    adapter.onToolPlan("session", List.of(new ToolCall("id", "echo", Map.of("text", "hi"))));
    adapter.onToolExecution(
        "session",
        new ToolExecution(
            new ToolCall("id", "echo", Map.of()), "ok", "done", java.time.Instant.now(), 1));
    adapter.onReasoningStart("session");
    adapter.onToolRepair("session");

    String printed = output.toString(StandardCharsets.UTF_8);
    assertThat(printed).contains("\"event\":\"tool_plan\"");
    assertThat(printed).contains("\"event\":\"tool_execution\"");
    assertThat(printed).contains("\"event\":\"reasoning_start\"");
    assertThat(printed).contains("\"event\":\"tool_repair\"");
  }

  private static void setField(final Object target, final String fieldName, final Object value)
      throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void invokeInit(
      final StdioAgentServer server, final String agentName, final String configPath)
      throws Exception {
    var method = StdioAgentServer.class.getDeclaredMethod("init", String.class, String.class);
    method.setAccessible(true);
    method.invoke(server, agentName, configPath);
  }

  private static final class FakeAgent implements AgentEngine {
    @Override
    public Message invoke(final String sessionId, final Message message) {
      return new Message(Role.ASSISTANT, "done", "", List.of(), List.of());
    }

    @Override
    public void registerListener(final com.agentengine.engine.AgentListener listener) {}

    @Override
    public List<Message> buildPrompt(final String sessionId) {
      return List.of(Message.system("sys"));
    }
  }

  private static final class BuildPromptRequestTest extends BuildPromptRequest {
    private BuildPromptRequestTest() {
      super();
    }
  }

  private static final class TestEventPublisher implements AgentEventPublisher {
    private final StdioAgentServer server;

    private TestEventPublisher(final StdioAgentServer server) {
      this.server = server;
    }

    @Override
    public void publish(final com.agentengine.engine.events.AgentEvent event) {
      invokeSendEvent(server, event.sessionId(), event.event(), event.payload());
    }
  }

  private static void invokeSendEvent(
      final StdioAgentServer server, final String sessionId, final String event, final Object payload) {
    try {
      var method = StdioAgentServer.class.getDeclaredMethod("sendEvent", String.class, String.class, Object.class);
      method.setAccessible(true);
      method.invoke(server, sessionId, event, payload);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
