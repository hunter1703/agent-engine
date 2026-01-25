package com.agentengine.engine;

import static java.lang.StringTemplate.STR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryStateStore;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.UserClarificationTool;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HybridAgentTest {

  @Test
  void invokeRunsPlanAndReturnsFinalAnswer() {
    InMemoryMessageStore stateStore = new InMemoryMessageStore();
    String sessionId = "session";

    // First response: update_plan with a step and a tool call for that step
    String planAndToolPayload = """
        {"toolCalls":[{"id":"plan-1","name":"update_plan","args":{"plan":[{"step":"echo hi","status":"in_progress"}]}},{"id":"call-1","name":"echo","args":{"text":"hi"}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planAndToolPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    List<Tool> tools = List.of(new EchoTool());
    ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(new EchoTool()));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(reasoningModel, routerModel, planningAgent,
        toolExecutor, new InMemorySessionStore(), 2, "test-agent");

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.planPayloads).hasSize(1);
    assertThat(listener.planPayloads.getFirst()).contains("echo hi");
    assertThat(listener.toolResults.values().iterator().next()).isEqualTo("hi");
  }

  @Test
  void invokeHandlesUnknownTool() {
    InMemoryMessageStore stateStore = new InMemoryMessageStore();
    String sessionId = "session";

    // Response with an unknown tool call
    String unknownToolPayload = """
        {"toolCalls":[{"id":"call-1","name":"unknown","args":{}}]}
        """;
    Message first = new Message(Role.ASSISTANT, unknownToolPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    ToolExecutor toolExecutor = new DefaultToolExecutor(List.of());

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(reasoningModel, routerModel, planningAgent,
        toolExecutor, new InMemorySessionStore(), 2, "test-agent");

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolResults.values()).anyMatch(value -> value.contains("Unknown tool"));
  }

  @Test
  void invokeHandlesReasoningRepair() {
    InMemoryMessageStore stateStore = new InMemoryMessageStore();
    String sessionId = "session";

    Message invalid = new Message(Role.ASSISTANT, "", "", List.of());
    Message valid = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, new ArrayList<>(List.of(invalid, valid)),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));
    ToolExecutor toolExecutor = mock(ToolExecutor.class);

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(reasoningModel, routerModel, planningAgent,
        toolExecutor, new InMemorySessionStore(), 5, "test-agent");

    engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    List<Message> reasoningMessages = stateStore.getMessages("test-agent", sessionId);
    assertThat(reasoningMessages)
        .anyMatch(m -> m.getRole() == Role.SYSTEM && m.getContent().contains("tool call"));
  }

  @Test
  void clarificationKeepsRunOpenUntilFinalAnswer() {
    InMemoryMessageStore stateStore = new InMemoryMessageStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"update_plan","args":{"plan":[{"step":"clarify","status":"pending"}]}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    List<Tool> tools = List.of(new UserClarificationTool());
    ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(new UserClarificationTool()));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(reasoningModel, routerModel, planningAgent,
        toolExecutor, new InMemorySessionStore(), 2, "test-agent");

    RunListener listener = new RunListener();
    engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(listener.runStarted).isEqualTo(1);
    assertThat(listener.runFinished).isEqualTo(0);

    Message result = engine.invoke(sessionId, Message.user("clarification"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.runStarted).isEqualTo(1);
    assertThat(listener.runFinished).isEqualTo(1);
  }

  @Test
  void invokeReturnsLimitExceededMessage() {
    InMemoryMessageStore stateStore = new InMemoryMessageStore();
    String sessionId = "session";

    Message empty = new Message(Role.ASSISTANT, "", null, List.of());
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(empty, empty, empty),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(reasoningModel, routerModel, planningAgent,
        mock(ToolExecutor.class), new InMemorySessionStore(), 1, "test-agent");

    Message result = engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    assertThat(result.getContent()).isEqualTo("Number of assistant invocations exceeded maximum : 1");
  }

  @Test
  void planningAgentSeedsExecutableSteps() {
    InMemoryMessageStore stateStore = new InMemoryMessageStore();
    String sessionId = "session";

    Message empty = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"\"}", null, null);
    Message finalAnswer = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    CapturingModel reasoningModel = new CapturingModel(ResponseFormatType.JSON, List.of(empty, finalAnswer),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    LLMModel routerModel = routerModel(true);

    LLMModel tasksModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "- step one\n- step two", null, null)),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));
    PlanningAgent planningAgent = new PlanningAgent(tasksModel);

    HybridAgent engine = new HybridAgent(reasoningModel, routerModel, planningAgent,
        new DefaultToolExecutor(List.of()), new InMemorySessionStore(), 2, "test-agent");

    engine.invoke(sessionId, Message.user("complex"), new CapturingListener());

    assertThat(reasoningModel.prompts())
        .anyMatch(prompt -> prompt.stream().anyMatch(message ->
            message.getRole() == Role.SYSTEM && message.getContent().contains("Current plan step: step one")));
  }

  private static final class QueueModel implements LLMModel {
    private final Deque<Message> responses;
    private final ResponseFormatType responseFormat;
    private final ContextManager contextManager;

    private QueueModel(final ResponseFormatType type, final List<Message> responses, final ContextManager contextManager) {
      this.responses = new ArrayDeque<>(responses);
      this.responseFormat = type;
      this.contextManager = contextManager;
    }

    @Override
    public Message generate(final List<Message> messages) {
      if (responses.isEmpty()) {
        return new Message(Role.ASSISTANT, "", null, null);
      }
      return responses.removeFirst();
    }

    @Override
    public ResponseFormatType responseFormat() {
      return responseFormat;
    }

    @Override
    public boolean thoughtsEnabled() {
      return true;
    }

    @Override
    public String thoughtsStartTag() {
      return "<think>";
    }

    @Override
    public String thoughtsEndTag() {
      return "</think>";
    }

    @Override
    public ContextManager getContextManager() {
      return contextManager;
    }
  }

  private record EchoTool() implements Tool {
    @Override
    public String name() {
      return "echo";
    }

    @Override
    public String description() {
      return "Echoes text";
    }

    @Override
    public String execute(final Map<String, Object> args) {
      return args.get("text").toString();
    }
  }

  private static final class CapturingModel implements LLMModel {
    private final Deque<Message> responses;
    private final ResponseFormatType responseFormat;
    private final ContextManager contextManager;
    private final List<List<Message>> prompts = new ArrayList<>();

    private CapturingModel(final ResponseFormatType type, final List<Message> responses,
        final ContextManager contextManager) {
      this.responses = new ArrayDeque<>(responses);
      this.responseFormat = type;
      this.contextManager = contextManager;
    }

    @Override
    public Message generate(final List<Message> messages) {
      prompts.add(messages);
      if (responses.isEmpty()) {
        return new Message(Role.ASSISTANT, "", null, null);
      }
      return responses.removeFirst();
    }

    @Override
    public ResponseFormatType responseFormat() {
      return responseFormat;
    }

    @Override
    public boolean thoughtsEnabled() {
      return true;
    }

    @Override
    public String thoughtsStartTag() {
      return "<think>";
    }

    @Override
    public String thoughtsEndTag() {
      return "</think>";
    }

    @Override
    public ContextManager getContextManager() {
      return contextManager;
    }

    private List<List<Message>> prompts() {
      return prompts;
    }
  }

  private static final class CapturingListener implements AgentListener {
    private final Map<String, String> toolResults = new HashMap<>();
    private final Map<String, String> toolNames = new HashMap<>();
    private final List<String> planPayloads = new ArrayList<>();

    @Override
    public void onToolCallStart(final String sessionId, final String toolCallId, final String toolCallName) {
      toolNames.put(toolCallId, toolCallName);
    }

    @Override
    public void onToolCallArgs(final String sessionId, final String toolCallId, final String delta) {
      if ("update_plan".equalsIgnoreCase(toolNames.get(toolCallId))) {
        planPayloads.add(delta);
      }
    }

    @Override
    public void onToolCallResult(String sessionId, String toolCallId, String content) {
      toolResults.put(toolCallId, content);
    }

  }

  private static final class RunListener implements AgentListener {
    private int runStarted;
    private int runFinished;

    @Override
    public void onRunStarted(final String sessionId, final String runId) {
      runStarted++;
    }

    @Override
    public void onRunFinished(final String sessionId, final String runId) {
      runFinished++;
    }
  }

  private static PlanningAgent createTasksAgent() {
    LLMModel tasksModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "tasks", null, null)),
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of()));
    return new PlanningAgent(tasksModel);
  }

  private static LLMModel routerModel(final boolean complex) {
    String payload = STR."{\"complex\":\{complex}}";
    return new QueueModel(ResponseFormatType.JSON, List.of(new Message(Role.ASSISTANT, payload, null, null)),
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of()));
  }
}
