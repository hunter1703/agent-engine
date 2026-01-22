package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.context.BaseContextBuilder;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.api.state.SessionStore;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.api.beans.session.ToolRequest;
import com.agentengine.engine.tools.AgenticToolExecutor;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.ToolRequestExecutor;
import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.api.AgentListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;

import java.util.*;

import org.junit.jupiter.api.Test;

class HybridAgentTest {

  @Test
  void invokeRunsToolPlanAndReturnsFinalAnswer() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    String toolRequest = "TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\",\"args\":{\"text\":\"hi\"}}";
    Message first = new Message(Role.ASSISTANT, "<think>plan</think>\n" + toolRequest, null, null, null);
    Message second = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(first, second));

    String toolAssistantPayload = """
        {"toolRequests":[{"id":"call-1","name":"echo","args":{"text":"hi"}}]}
        """;
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null, null)));

    List<Tool> tools = List.of(new EchoTool());
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    Map<String, Tool> toolMap = new HashMap<>();
    tools.forEach(t -> toolMap.put(t.name(), t));
    BaseContextBuilder toolContextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    AgenticToolExecutor toolExecutor = new ToolRequestExecutor(toolAssistantModel, toolContextBuilder, toolMap, sessionStore);
    
    HybridAgent engine = new HybridAgent(reasoningModel, toolExecutor, reasoningContext,
        sessionStore, 2);

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolPlans).hasSize(1);
    assertThat(listener.toolPlans.getFirst().iterator().next().name()).isEqualTo("echo");
    assertThat(listener.toolResults).hasSize(1);
    assertThat(listener.toolResults.values().iterator().next()).isEqualTo("hi");

    List<Message> reasoningMessages = sessionStore.getMessages(STR."\{sessionId}_reasoning");
    assertThat(reasoningMessages).anyMatch(message -> message.getRole() == Role.USER
        && message.getContent().contains("tool_calls"));
  }

  @Test
  void invokeHandlesToolExecution() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    String toolRequest = "TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\",\"args\":{\"text\":\"hi\"}}";
    Message first = new Message(Role.ASSISTANT, toolRequest, null, null, null);
    Message second = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(first, second));

    String toolPayload = "{" + "\"toolRequests\":[{\"id\":\"call-1\",\"name\":\"echo\"}]" + "}";
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolPayload, null, null, null)));

    EchoTool tool = new EchoTool(); // Using EchoTool instead of FlakyTool since retries are removed
    List<Tool> tools = List.of(tool);
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    BaseContextBuilder toolContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);

    Map<String, Tool> toolMap = new HashMap<>();
    tools.forEach(t -> toolMap.put(t.name(), t));
    BaseContextBuilder toolContextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    AgenticToolExecutor toolExecutor = new ToolRequestExecutor(toolAssistantModel, toolContextBuilder, toolMap,
        sessionStore);

    HybridAgent engine = new HybridAgent(reasoningModel, toolExecutor, reasoningContext, sessionStore, 2);

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    // With simplified logic, no repairs should be triggered
    assertThat(listener.toolRepairs).isEqualTo(0);
    assertThat(listener.toolResults).isNotEmpty();

    List<Message> toolMessages = sessionStore.getMessages(sessionId + "_tool");
    assertThat(toolMessages).isNotEmpty();
  }

  @Test
  void invokeReturnsNullWhenInvocationLimitReached() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    Message empty = new Message(Role.ASSISTANT, "", null, null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(empty, empty, empty));
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "", null, null, null)));

    List<Tool> tools = List.of();
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    BaseContextBuilder toolContextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
    AgenticToolExecutor toolExecutor = new ToolRequestExecutor(toolAssistantModel, toolContextBuilder, Map.of(),
        sessionStore);

    HybridAgent engine = new HybridAgent(reasoningModel, toolExecutor, reasoningContext, sessionStore, 1);

    Message result = engine.invoke(sessionId, Message.user("hello"), new AgentListener() {
      @Override
      public void onFinalAnswer(String sessionId, Message message) {
        // Not needed for this test
      }
    });

    assertThat(result.getContent()).isEqualTo("Number of assistant invocations exceeded maximum : 1");
  }

  @Test
  void invokeHandlesUnknownToolWithoutEmittingExecutionEvent() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    String toolRequest = "TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"unknown\"}";
    Message first = new Message(Role.ASSISTANT, toolRequest, null, null, null);
    Message second = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(first, second));

    String toolAssistantPayload = "{" + "\"toolRequests\":[{\"id\":\"call-1\",\"name\":\"unknown\"}]" + "}";
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null, null)));

    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
    BaseContextBuilder toolContextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
    AgenticToolExecutor toolExecutor = new ToolRequestExecutor(toolAssistantModel, toolContextBuilder, Map.of(),
        sessionStore);

    HybridAgent engine = new HybridAgent(reasoningModel, toolExecutor, reasoningContext, sessionStore, 5);

    CapturingListener listener = new CapturingListener();
    Message result = engine.invoke(sessionId, Message.user("hello"), listener);

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolResults).hasSize(1);
    assertThat(listener.toolResults.values().iterator().next()).contains("Unknown tool");
    List<Message> reasoningMessages = sessionStore.getMessages(sessionId + "_reasoning");
    assertThat(reasoningMessages)
        .anyMatch(message -> message.getRole() == Role.USER && message.getContent().contains("Unknown tool"));
  }

  @Test
  void invokeParsesToolCallsFromTextPayload() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    String toolRequest = "TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\"}";
    Message first = new Message(Role.ASSISTANT, toolRequest, null, null, null);
    Message second = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(first, second));

    String toolPayload = "{\"toolRequests\":[{\"id\":\"call-1\",\"name\":\"echo\",\"args\":{}}]}";
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, toolPayload, null, null, null)));

    List<Tool> tools = List.of(new EchoTool());
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    Map<String, Tool> toolMap = new HashMap<>();
    tools.forEach(t -> toolMap.put(t.name(), t));
    BaseContextBuilder toolContextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    AgenticToolExecutor toolExecutor = new ToolRequestExecutor(toolAssistantModel, toolContextBuilder, toolMap,
        sessionStore);

    HybridAgent engine = new HybridAgent(reasoningModel, toolExecutor, reasoningContext, sessionStore, 2);

    Message result = engine.invoke(sessionId, Message.user("hello"), new AgentListener() {
      @Override
      public void onFinalAnswer(String sessionId, Message message) {
        // Not needed for this test
      }
    });

    assertThat(result.getContent()).isEqualTo("done");
    List<Message> toolMessages = sessionStore.getMessages(sessionId + "_tool");
    assertThat(toolMessages).anyMatch(message -> message.getRole() == Role.ASSISTANT);
  }

  @Test
  void invokeHandlesNullToolExecutions() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    String toolAssistantPayload = "{" + "\"toolRequests\":[{\"id\":\"call-1\",\"name\":\"unknown\"}]" + "}";
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON, new ArrayList<>(
        List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, List.of("TOOL_REQUEST: {}"), null))));

    AgenticToolExecutor toolExecutor = mock(AgenticToolExecutor.class);
    when(toolExecutor.executeRequests(anyString(), anyList(), any())).thenReturn(List.of());

    HybridAgent engine = new HybridAgent(reasoningModel, toolExecutor, mock(BaseContextBuilder.class), sessionStore, 1);

    CapturingListener listener = new CapturingListener();
    engine.invoke(sessionId, Message.user("hello"), listener);

    List<Message> reasoningMessages = sessionStore.getMessages(sessionId + "_reasoning");
    assertThat(reasoningMessages).anyMatch(m -> m.getContent().contains("Unable to execute tools"));
  }

  @Test
  void invokeHandlesReasoningRepair() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    // First message is invalid (empty), second is valid
    Message invalid = new Message(Role.ASSISTANT, "", "", List.of(), List.of());
    Message valid = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, new ArrayList<>(List.of(invalid, valid)));
    AgenticToolExecutor toolExecutor = mock(AgenticToolExecutor.class);

    HybridAgent engine = new HybridAgent(reasoningModel, toolExecutor, mock(BaseContextBuilder.class), sessionStore, 5);

    CapturingListener listener = new CapturingListener();
    engine.invoke(sessionId, Message.user("hello"), listener);

    List<Message> reasoningMessages = sessionStore.getMessages(sessionId + "_reasoning");
    assertThat(reasoningMessages).anyMatch(
        m -> m.getRole() == Role.SYSTEM && m.getContent().contains("You must provide a final answer or tool requests"));
  }

  @Test
  void invokeExhaustsReasoningRetries() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    // Always empty message to exhaust retries
    Message invalid = new Message(Role.ASSISTANT, "", "", List.of(), List.of());

    // 3 retries max configured
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT,
        new ArrayList<>(List.of(invalid, invalid, invalid, invalid, invalid)));

    HybridAgent engine = new HybridAgent(reasoningModel, mock(AgenticToolExecutor.class),
        mock(BaseContextBuilder.class), sessionStore, 3);

    Message result = engine.invoke(sessionId, Message.user("hello"), new CapturingListener());

    assertThat(result.getContent()).isEmpty();
    List<Message> reasoningMessages = sessionStore.getMessages(sessionId + "_reasoning");
    // 7 repair messages: 6 from _runReasoner (initial + 5 retries) + 1 from invoke
    // loop
    long sysMessages = reasoningMessages.stream().filter(m -> m.getRole() == Role.SYSTEM).count();
    assertThat(sysMessages).isEqualTo(7);
  }

  @Test
  void invokeHandlesEmptyToolAssistantResponse() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    String toolRequest = "TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\"}";
    Message first = new Message(Role.ASSISTANT, toolRequest, null, null, null);
    Message second = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(first, second));

    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "", null, null, null)));

    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
    BaseContextBuilder toolContextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
    AgenticToolExecutor toolExecutor = new ToolRequestExecutor(toolAssistantModel, toolContextBuilder, Map.of(), sessionStore);
    
    HybridAgent agent = new HybridAgent(reasoningModel, toolExecutor, reasoningContext,
        sessionStore, 2);

    Message result = agent.invoke(sessionId, Message.user("hello"), new AgentListener() {
      @Override
      public void onFinalAnswer(String sessionId, Message message) {
        // Not needed for this test
      }
    });

    assertThat(result.getContent()).isEqualTo("done");
    List<Message> reasoningMessages = sessionStore.getMessages(STR."\{sessionId}_reasoning");
    assertThat(reasoningMessages).noneMatch(message -> message.getRole() == Role.USER
        && message.getContent().contains("tool_calls"));
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
        return new Message(Role.ASSISTANT, "", null, null, null);
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

  private static final class FlakyTool implements Tool {
    private int invocations = 0;

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
      invocations++;
      if (invocations == 1) {
        throw new IllegalStateException("fail once");
      }
      return "ok";
    }
  }

  private static final class CapturingListener implements AgentListener {
    private final Map<String, String> toolResults = new HashMap<>();
    private final List<Collection<ToolCall>> toolPlans = new ArrayList<>();
    private int toolRepairs = 0;
    private final List<String> finalAnswers = new ArrayList<>();
    private final List<String> startedRuns = new ArrayList<>();
    private final List<String> finishedRuns = new ArrayList<>();
    private final List<String> startedSteps = new ArrayList<>();
    private final List<String> finishedSteps = new ArrayList<>();
    private final List<String> reasoningDeltas = new ArrayList<>();
    private int reasoningMessageStarts = 0;
    private int reasoningMessageEnds = 0;
    private final List<String> textMessageStarts = new ArrayList<>();
    private final List<String> textMessageDeltas = new ArrayList<>();
    private final List<String> textMessageEnds = new ArrayList<>();

    @Override
    public void onRunStarted(String sessionId, String runId) {
      startedRuns.add(runId);
    }

    @Override
    public void onRunFinished(String sessionId, String runId) {
      finishedRuns.add(runId);
    }

    @Override
    public void onStepStarted(String sessionId, String stepName) {
      startedSteps.add(stepName);
    }

    @Override
    public void onStepFinished(String sessionId, String stepName) {
      finishedSteps.add(stepName);
    }

    @Override
    public void onTextMessageStart(String sessionId, String messageId, String role) {
      textMessageStarts.add(messageId);
    }

    @Override
    public void onTextMessageDelta(String sessionId, String messageId, String delta) {
      textMessageDeltas.add(delta);
    }

    @Override
    public void onTextMessageEnd(String sessionId, String messageId) {
      textMessageEnds.add(messageId);
    }

    @Override
    public void onReasoningMessageStart(String sessionId, String messageId, String role) {
      reasoningMessageStarts++;
    }

    @Override
    public void onReasoningMessageDelta(String sessionId, String messageId, String delta) {
      reasoningDeltas.add(delta);
    }

    @Override
    public void onReasoningMessageEnd(String sessionId, String messageId) {
      reasoningMessageEnds++;
    }

    @Override
    public void onToolPlan(final String sessionId, final Collection<ToolCall> toolCalls) {
      toolPlans.add(toolCalls);
    }

    @Override
    public void onToolCallResult(String sessionId, String toolCallId, String content) {
      toolResults.put(toolCallId, content);
    }

    @Override
    public void onToolRepair(final String sessionId, final List<ToolCall> toolCalls,
        final List<ToolRequest> remainingRequests) {
      toolRepairs++;
    }

    @Override
    public void onFinalAnswer(final String sessionId, final Message message) {
      finalAnswers.add(message.getContent());
    }
  }
}
