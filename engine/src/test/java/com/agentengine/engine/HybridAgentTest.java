package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.agents.ToolAssistantAgent;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.state.InMemoryStateStore;
import com.agentengine.engine.api.StateStore;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.UserClarificationTool;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
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
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"plan_items","args":{"items":[{"id":"step-1","description":"echo hi"}]}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second));

    String toolAssistantPayload = """
        {"toolCalls":[{"id":"call-1","name":"echo","args":{"text":"hi"}}]}
        """;
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null)));

    List<Tool> tools = List.of(new EchoTool());
    BaseContextManager reasoningContext = new BaseContextManager(stateStore, "system", "protocol", tools);
    BaseContextManager toolContextBuilder = new BaseContextManager(stateStore, "system", "protocol", tools);
    Map<String, Tool> toolMap = new HashMap<>();
    tools.forEach(t -> toolMap.put(t.name(), t));
    ToolAssistantAgent toolAssistantAgent = new ToolAssistantAgent(toolAssistantModel, toolContextBuilder, stateStore);
    ToolExecutor toolExecutor = new DefaultToolExecutor(toolMap);

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, reasoningContext, stateStore, 2));

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.planPayloads).hasSize(1);
    assertThat(listener.planPayloads.getFirst()).contains("echo hi");
    assertThat(listener.toolResults).hasSize(1);
    assertThat(listener.toolResults.values().iterator().next()).isEqualTo("hi");
  }

  @Test
  void invokeHandlesUnknownTool() {
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"plan_items","args":{"items":[{"id":"step-1","description":"unknown action"}]}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second));

    String toolAssistantPayload = """
        {"toolCalls":[{"id":"call-1","name":"unknown","args":{}}]}
        """;
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null)));

    BaseContextManager reasoningContext = new BaseContextManager(stateStore, "system", "protocol", List.of());
    BaseContextManager toolContextBuilder = new BaseContextManager(stateStore, "system", "protocol", List.of());
    ToolAssistantAgent toolAssistantAgent = new ToolAssistantAgent(toolAssistantModel, toolContextBuilder, stateStore);
    ToolExecutor toolExecutor = new DefaultToolExecutor(Map.of());

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, reasoningContext, stateStore, 2));

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolResults.values().iterator().next()).contains("Unknown tool");
  }

  @Test
  void invokeHandlesNullToolExecutions() {
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"plan_items","args":{"items":[{"id":"step-1","description":"plan"}]}}]}
        """;
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON,
        new ArrayList<>(List.of(new Message(Role.ASSISTANT, planPayload, null, null))));

    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    when(toolExecutor.execute(anyString(), anyString(), anyList(), any())).thenReturn(List.of());

    ToolAssistantAgent toolAssistantAgent = mock(ToolAssistantAgent.class);
    when(toolAssistantAgent.generateToolCalls(anyString(), anyString(), any(), any())).thenReturn(List.of());

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, mock(BaseContextManager.class), stateStore, 1));

    CapturingListener listener = new CapturingListener();
    engine.invoke(sessionId, Message.user("hello"), listener);

    List<Message> reasoningMessages = stateStore.getMessages(sessionId + "_reasoning");
    assertThat(reasoningMessages).anyMatch(m -> m.getContent().contains("Unable to execute plan item"));
  }

  @Test
  void invokeHandlesReasoningRepair() {
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    Message invalid = new Message(Role.ASSISTANT, "", "", List.of());
    Message valid = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, new ArrayList<>(List.of(invalid, valid)));
    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    ToolAssistantAgent toolAssistantAgent = mock(ToolAssistantAgent.class);
    when(toolAssistantAgent.generateToolCalls(anyString(), anyString(), any(), any())).thenReturn(List.of());

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, mock(BaseContextManager.class), stateStore, 5));

    engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    List<Message> reasoningMessages = stateStore.getMessages(sessionId + "_reasoning");
    assertThat(reasoningMessages)
        .anyMatch(m -> m.getRole() == Role.SYSTEM && m.getContent().contains("plan_items"));
  }

  @Test
  void invokeExhaustsReasoningRetries() {
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    Message invalid = new Message(Role.ASSISTANT, "", "", List.of());
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON,
        new ArrayList<>(List.of(invalid, invalid, invalid, invalid, invalid)));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    ToolAssistantAgent toolAssistantAgent = mock(ToolAssistantAgent.class);
    when(toolAssistantAgent.generateToolCalls(anyString(), anyString(), any(), any())).thenReturn(List.of());

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, mock(ToolExecutor.class), mock(BaseContextManager.class), stateStore, 3));

    Message result = engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    assertThat(result.getContent()).isEmpty();
    List<Message> reasoningMessages = stateStore.getMessages(sessionId + "_reasoning");
    long sysMessages = reasoningMessages.stream().filter(m -> m.getRole() == Role.SYSTEM).count();
    assertThat(sysMessages).isEqualTo(7);
  }

  @Test
  void invokeReturnsLimitExceededMessage() {
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    Message empty = new Message(Role.ASSISTANT, "", null, List.of());
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(empty, empty, empty));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    ToolAssistantAgent toolAssistantAgent = mock(ToolAssistantAgent.class);
    when(toolAssistantAgent.generateToolCalls(anyString(), anyString(), any(), any())).thenReturn(List.of());

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, mock(ToolExecutor.class), mock(BaseContextManager.class), stateStore, 1));

    Message result = engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    assertThat(result.getContent()).isEqualTo("Number of assistant invocations exceeded maximum : 1");
  }

  @Test
  void complexTaskGeneratesTaskList() {
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    Message finalAnswer = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(finalAnswer));

    LLMModel routerModel = routerModel(true);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    ToolAssistantAgent toolAssistantAgent = mock(ToolAssistantAgent.class);
    when(toolAssistantAgent.generateToolCalls(anyString(), anyString(), any(), any())).thenReturn(List.of());

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, mock(ToolExecutor.class), mock(BaseContextManager.class), stateStore, 1));

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("complex work"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolResults.values()).contains("tasks");
  }

  @Test
  void clarificationKeepsRunOpenUntilFinalAnswer() {
    StateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"plan_items","args":{"items":[{"id":"step-1","description":"clarify"}]}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second));

    String toolAssistantPayload = """
        {"toolCalls":[{"id":"call-1","name":"user_clarification","args":{"question":"Need input"}}]}
        """;
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null)));

    List<Tool> tools = List.of(new UserClarificationTool());
    BaseContextManager reasoningContext = new BaseContextManager(stateStore, "system", "protocol", tools);
    BaseContextManager toolContextBuilder = new BaseContextManager(stateStore, "system", "protocol", tools);
    Map<String, Tool> toolMap = Map.of("user_clarification", new UserClarificationTool());
    ToolAssistantAgent toolAssistantAgent = new ToolAssistantAgent(toolAssistantModel, toolContextBuilder, stateStore);
    ToolExecutor toolExecutor = new DefaultToolExecutor(toolMap);

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent(stateStore);

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, reasoningContext, stateStore, 2));

    RunListener listener = new RunListener();
    engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(listener.runStarted).isEqualTo(1);
    assertThat(listener.runFinished).isEqualTo(0);

    Message result = engine.invoke(sessionId, Message.user("clarification"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.runStarted).isEqualTo(1);
    assertThat(listener.runFinished).isEqualTo(1);
  }

  private static final class QueueModel implements LLMModel {
    private final Deque<Message> responses;
    private final ResponseFormat responseFormat;

    private QueueModel(final ResponseFormatType type, final List<Message> responses) {
      this.responses = new ArrayDeque<>(responses);
      this.responseFormat = new ResponseFormat.Builder().type(type).build();
    }

    @Override
    public Message generate(final List<Message> messages) {
      if (responses.isEmpty()) {
        return new Message(Role.ASSISTANT, "", null, null);
      }
      return responses.removeFirst();
    }

    @Override
    public ResponseFormat responseFormat() {
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

  private static final class CapturingListener implements AgentListener {
    private final Map<String, String> toolResults = new HashMap<>();
    private final List<String> planPayloads = new ArrayList<>();

    @Override
    public void onToolCallArgs(final String sessionId, final String toolCallId, final String delta) {
      if (delta != null && delta.contains("echo hi")) {
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

  private static PlanningAgent createTasksAgent(final StateStore stateStore) {
    LLMModel tasksModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "tasks", null, null)));
    BaseContextManager contextBuilder = new BaseContextManager(stateStore, "system", "protocol", List.of());
    return new PlanningAgent(tasksModel, contextBuilder, stateStore);
  }

  private static LLMModel routerModel(final boolean complex) {
    String payload = STR."{\"complex\":\{complex}}";
    return new QueueModel(ResponseFormatType.JSON, List.of(new Message(Role.ASSISTANT, payload, null, null)));
  }
}
