package com.agentengine.engine;

import static java.lang.StringTemplate.STR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.agents.ToolAssistantAgent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.PlanUpdate;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryStateStore;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.tools.DefaultToolHandler;
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
    InMemoryStateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"update_plan","args":{"plan":[{"step":"echo hi","status":"pending"}]}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    String toolAssistantPayload = """
        {"toolCalls":[{"id":"call-1","name":"echo","args":{"text":"hi"}}]}
        """;
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null)),
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of()));

    List<Tool> tools = List.of(new EchoTool());
    ToolAssistantAgent toolAssistantAgent = new ToolAssistantAgent(toolAssistantModel, tools, new InMemoryStateStore());
    ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(new DefaultToolHandler(new EchoTool())));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, reasoningModel.getContextManager(), 2));

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.planUpdates).hasSize(1);
    assertThat(listener.planUpdates.getFirst().plan().getFirst().step()).isEqualTo("echo hi");
    assertThat(listener.toolResults.values().iterator().next()).isEqualTo("hi");
  }

  @Test
  void invokeHandlesUnknownTool() {
    InMemoryStateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"update_plan","args":{"plan":[{"step":"unknown action","status":"pending"}]}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    String toolAssistantPayload = """
        {"toolCalls":[{"id":"call-1","name":"unknown","args":{}}]}
        """;
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null)),
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of()));

    ToolAssistantAgent toolAssistantAgent = new ToolAssistantAgent(toolAssistantModel, List.of(), new InMemoryStateStore());
    ToolExecutor toolExecutor = new DefaultToolExecutor(List.of());

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, reasoningModel.getContextManager(), 2));

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolResults.values()).anyMatch(value -> value.contains("Unknown tool"));
  }

  @Test
  void invokeHandlesReasoningRepair() {
    InMemoryStateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    Message invalid = new Message(Role.ASSISTANT, "", "", List.of());
    Message valid = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, new ArrayList<>(List.of(invalid, valid)),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));
    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    ToolAssistantAgent toolAssistantAgent = mock(ToolAssistantAgent.class);
    when(toolAssistantAgent.generateToolCalls(anyString(), anyString(), any(), any())).thenReturn(List.of());

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, reasoningModel.getContextManager(), 5));

    engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    List<Message> reasoningMessages = stateStore.getMessages(sessionId + "_reasoning");
    assertThat(reasoningMessages)
        .anyMatch(m -> m.getRole() == Role.SYSTEM && m.getContent().contains("tool call"));
  }

  @Test
  void clarificationKeepsRunOpenUntilFinalAnswer() {
    InMemoryStateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    String planPayload = """
        {"toolCalls":[{"id":"plan-1","name":"update_plan","args":{"plan":[{"step":"clarify","status":"pending"}]}}]}
        """;
    Message first = new Message(Role.ASSISTANT, planPayload, null, null);
    Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    String toolAssistantPayload = """
        {"toolCalls":[{"id":"call-1","name":"user_clarification","args":{"question":"Need input"}}]}
        """;
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null)),
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of()));

    List<Tool> tools = List.of(new UserClarificationTool());
    ToolAssistantAgent toolAssistantAgent = new ToolAssistantAgent(toolAssistantModel, tools, new InMemoryStateStore());
    ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(new DefaultToolHandler(new UserClarificationTool())));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, toolExecutor, reasoningModel.getContextManager(), 2));

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
    InMemoryStateStore stateStore = new InMemoryStateStore();
    String sessionId = "session";

    Message empty = new Message(Role.ASSISTANT, "", null, List.of());
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(empty, empty, empty),
        new BaseContextManager(stateStore, "system", "protocol", List.of()));

    LLMModel routerModel = routerModel(false);
    PlanningAgent planningAgent = createTasksAgent();

    ToolAssistantAgent toolAssistantAgent = mock(ToolAssistantAgent.class);
    when(toolAssistantAgent.generateToolCalls(anyString(), anyString(), any(), any())).thenReturn(List.of());

    HybridAgent engine = new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel,
        toolAssistantAgent, mock(ToolExecutor.class), reasoningModel.getContextManager(), 1));

    Message result = engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    assertThat(result.getContent()).isEqualTo("Number of assistant invocations exceeded maximum : 1");
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

  private static final class CapturingListener implements AgentListener {
    private final Map<String, String> toolResults = new HashMap<>();
    private final List<PlanUpdate> planUpdates = new ArrayList<>();

    @Override
    public void onToolCallResult(String sessionId, String toolCallId, String content) {
      toolResults.put(toolCallId, content);
    }

    @Override
    public void onPlanUpdate(final String sessionId, final PlanUpdate update) {
      planUpdates.add(update);
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
