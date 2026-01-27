package com.agentengine.engine;

import static java.lang.StringTemplate.STR;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryMessageStore;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.utils.MessageParser;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.api.Tool;
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
  private static final String PLANNING_ROLE = "planning";
  private static final String REASONING_ROLE = "reasoning";
  private static final String ROUTER_ROLE = "router";

  @Test
  void invokeRunsPlanAndReturnsFinalAnswer() {
    final InMemoryMessageStore stateStore = new InMemoryMessageStore();
    final String sessionId = "session";

    // First response: update_plan with a step and a tool call for that step
    final String planAndToolPayload = """
        {"toolCalls":[{"id":"plan-1","name":"update_plan","args":{"plan":[{"step":"echo hi","status":"in_progress"}]}},{"id":"call-1","name":"echo","args":{"text":"hi"}}]}
        """;
    final Message first = new Message(Role.ASSISTANT, planAndToolPayload, null, null);
    final Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    final LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(REASONING_ROLE, stateStore, "system", "protocol", List.of()));

    final PlanningAgent planningAgent = createTasksAgent();
    final ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(planningAgent, new EchoTool()));
    final LLMModel routerModel = routerModel(false);

    final HybridAgent engine = new HybridAgent(reasoningModel, routerModel, toolExecutor, new InMemorySessionStore(), 2,
        "test-agent");

    final CapturingListener listener = new CapturingListener();
    final Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.planPayloads).hasSize(1);
    assertThat(listener.planPayloads.getFirst()).contains("echo hi");
    assertThat(listener.toolResults.values().iterator().next()).isEqualTo("hi");
  }

  @Test
  void invokeHandlesUnknownTool() {
    final InMemoryMessageStore stateStore = new InMemoryMessageStore();
    final String sessionId = "session";

    // Response with an unknown tool call
    final String unknownToolPayload = """
        {"toolCalls":[{"id":"call-1","name":"unknown","args":{}}]}
        """;
    final Message first = new Message(Role.ASSISTANT, unknownToolPayload, null, null);
    final Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    final LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(REASONING_ROLE, stateStore, "system", "protocol", List.of()));

    final PlanningAgent planningAgent = createTasksAgent();
    final ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(planningAgent));
    final LLMModel routerModel = routerModel(false);

    final HybridAgent engine = new HybridAgent(reasoningModel, routerModel, toolExecutor, new InMemorySessionStore(), 2,
        "test-agent");

    final CapturingListener listener = new CapturingListener();
    final Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolResults.values()).anyMatch(value -> value.contains("Unknown tool"));
  }

  @Test
  void invokeHandlesReasoningRepair() {
    final InMemoryMessageStore stateStore = new InMemoryMessageStore();
    final String sessionId = "session";

    final Message invalid = new Message(Role.ASSISTANT, "", "", List.of());
    final Message valid = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);

    final LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, new ArrayList<>(List.of(invalid, valid)),
        new BaseContextManager(REASONING_ROLE, stateStore, "system", "protocol", List.of()));
    final PlanningAgent planningAgent = createTasksAgent();
    final ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(planningAgent));
    final LLMModel routerModel = routerModel(false);

    final HybridAgent engine = new HybridAgent(reasoningModel, routerModel, toolExecutor, new InMemorySessionStore(), 5,
        "test-agent");

    engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    final List<Message> reasoningMessages = stateStore.getMessages(sessionId, REASONING_ROLE);
    assertThat(reasoningMessages).anyMatch(m -> m.getRole() == Role.SYSTEM && m.getContent().contains("tool call"));
  }

  @Test
  void clarificationKeepsRunOpenUntilFinalAnswer() {
    final InMemoryMessageStore stateStore = new InMemoryMessageStore();
    final String sessionId = "session";

    final String clarificationPayload = """
        {"toolCalls":[{"id":"clarify-1","name":"user_clarification","args":{"prompt":"Need more details"}}]}
        """;
    final Message first = new Message(Role.ASSISTANT, clarificationPayload, null, null);
    final Message second = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    final LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(first, second),
        new BaseContextManager(REASONING_ROLE, stateStore, "system", "protocol", List.of()));

    final PlanningAgent planningAgent = createTasksAgent();
    final ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(planningAgent, new UserClarificationTool()));
    final LLMModel routerModel = routerModel(false);

    final HybridAgent engine = new HybridAgent(reasoningModel, routerModel, toolExecutor, new InMemorySessionStore(), 2,
        "test-agent");

    final RunListener listener = new RunListener();
    engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(listener.runStarted).isEqualTo(1);
    assertThat(listener.runFinished).isEqualTo(0);

    final Message result = engine.invoke(sessionId, Message.user("clarification"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.runStarted).isEqualTo(1);
    assertThat(listener.runFinished).isEqualTo(1);
  }

  @Test
  void invokeReturnsLimitExceededMessage() {
    final InMemoryMessageStore stateStore = new InMemoryMessageStore();
    final String sessionId = "session";

    final Message empty = new Message(Role.ASSISTANT, "", null, List.of());
    final LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, List.of(empty, empty, empty),
        new BaseContextManager(REASONING_ROLE, stateStore, "system", "protocol", List.of()));

    final LLMModel routerModel = routerModel(false);
    final PlanningAgent planningAgent = createTasksAgent();

    final HybridAgent engine = new HybridAgent(reasoningModel, routerModel,
        new DefaultToolExecutor(List.of(planningAgent)), new InMemorySessionStore(), 1, "test-agent");

    final Message result = engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    assertThat(result.getContent()).isEqualTo("Number of assistant invocations exceeded maximum : 1");
  }

  @Test
  void planningAgentSeedsExecutableSteps() {
    final InMemoryMessageStore stateStore = new InMemoryMessageStore();
    final String sessionId = "session";

    final Message empty = new Message(Role.ASSISTANT, "{\"thoughts\":\"working\",\"finalAnswer\":\"\"}", null, null);
    final Message finalAnswer = new Message(Role.ASSISTANT, "{\"finalAnswer\":\"done\"}", null, null);
    final CapturingModel reasoningModel = new CapturingModel(ResponseFormatType.JSON, List.of(empty, finalAnswer),
        new BaseContextManager(REASONING_ROLE, stateStore, "system", "protocol", List.of()));

    final LLMModel routerModel = routerModel(true);

    final LLMModel tasksModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "- step one\n- step two", null, null)),
        new BaseContextManager(PLANNING_ROLE, stateStore, "system", "protocol", List.of()));
    final PlanningAgent planningAgent = new PlanningAgent(tasksModel);

    final HybridAgent engine = new HybridAgent(reasoningModel, routerModel,
        new DefaultToolExecutor(List.of(planningAgent)), new InMemorySessionStore(), 2, "test-agent");

    engine.invoke(sessionId, Message.user("complex"), new CapturingListener());

    assertThat(reasoningModel.prompts()).anyMatch(prompt -> prompt.stream().anyMatch(
        message -> message.getRole() == Role.SYSTEM && message.getContent().contains("Current plan step: step one")));
  }

  private static final class QueueModel implements LLMModel {
    private final Deque<Message> responses;
    private final ResponseFormatType responseFormat;
    private final ContextManager contextManager;
    private final MessageParser messageParser;

    private QueueModel(final ResponseFormatType type, final List<Message> responses,
        final ContextManager contextManager) {
      this.responses = new ArrayDeque<>(responses);
      this.responseFormat = type;
      this.contextManager = contextManager;
      this.messageParser = MessageParser.create().withResponseFormat(responseFormat()).toolCallingAllowed(true)
          .parseToolCallsFromText(true).areThoughtsEnabled(thoughtsEnabled()).withThoughtsStartTag(thoughtsStartTag())
          .withThoughtsEndTag(thoughtsEndTag());
    }

    @Override
    public Message generate(final List<Message> messages) {
      if (responses.isEmpty()) {
        return new Message(Role.ASSISTANT, "", null, null);
      }
      Message response = responses.removeFirst();
      return messageParser.parse(response); // Apply the same parsing as the real model
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
    private final MessageParser messageParser;
    private final List<List<Message>> prompts = new ArrayList<>();

    private CapturingModel(final ResponseFormatType type, final List<Message> responses,
        final ContextManager contextManager) {
      this.responses = new ArrayDeque<>(responses);
      this.responseFormat = type;
      this.contextManager = contextManager;
      this.messageParser = MessageParser.create().withResponseFormat(responseFormat()).toolCallingAllowed(true)
          .parseToolCallsFromText(true).areThoughtsEnabled(thoughtsEnabled()).withThoughtsStartTag(thoughtsStartTag())
          .withThoughtsEndTag(thoughtsEndTag());
    }

    @Override
    public Message generate(final List<Message> messages) {
      prompts.add(messages);
      if (responses.isEmpty()) {
        return new Message(Role.ASSISTANT, "", null, null);
      }
      Message response = responses.removeFirst();
      return messageParser.parse(response); // Apply the same parsing as the real model
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
    public void onToolCallResult(final String sessionId, final String toolCallId, final String content) {
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
    final LLMModel tasksModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "tasks", null, null)),
        new BaseContextManager(PLANNING_ROLE, new InMemoryMessageStore(), "system", "protocol", List.of()));
    return new PlanningAgent(tasksModel);
  }

  private static LLMModel routerModel(final boolean complex) {
    final String payload = STR."{\"complex\":\{complex}}";
    return new QueueModel(ResponseFormatType.JSON, List.of(new Message(Role.ASSISTANT, payload, null, null)),
        new BaseContextManager(ROUTER_ROLE, new InMemoryMessageStore(), "system", "protocol", List.of()));
  }
}
