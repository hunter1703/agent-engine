package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.context.BaseContextBuilder;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.Role;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.state.SessionStore;
import com.agentengine.engine.tools.AgentTool;
import com.agentengine.engine.utils.ToolRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HybridEngineTest {

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

    List<AgentTool> tools = List.of(new EchoTool());
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    BaseContextBuilder toolContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    HybridEngine engine = new HybridEngine(reasoningModel, toolAssistantModel, tools, reasoningContext, toolContext,
        sessionStore, 2);

    CapturingListener listener = new CapturingListener();
    engine.registerListener("session", listener);

    Message result = engine.invoke(sessionId, Message.user("hello"));

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolPlans).hasSize(1);
    assertThat(listener.toolPlans.getFirst().getFirst().name()).isEqualTo("echo");
    assertThat(listener.toolExecutions).hasSize(1);
    assertThat(listener.toolExecutions.getFirst().getOutput()).isEqualTo("hi");

    List<Message> reasoningMessages = sessionStore.getMessages(STR."\{sessionId}_reasoning");
    assertThat(reasoningMessages).anyMatch(message -> message.getRole() == Role.USER
        && message.getContent().contains("tool_calls"));
  }

  @Test
  void invokeTriggersToolRepairAndHandlesToolFailure() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    String toolRequest = "TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\",\"args\":{\"text\":\"hi\"}}";
    Message first = new Message(Role.ASSISTANT, toolRequest, null, null, null);
    Message second = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(first, second));

    String badToolPayload = "{" + "\"toolRequests\":[{\"id\":\"wrong\",\"name\":\"echo\"}]" + "}";
    String goodToolPayload = "{" + "\"toolRequests\":[{\"id\":\"call-1\",\"name\":\"echo\"}]" + "}";
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
        List.of(new Message(Role.ASSISTANT, badToolPayload, null, null, null),
            new Message(Role.ASSISTANT, goodToolPayload, null, null, null),
            new Message(Role.ASSISTANT, goodToolPayload, null, null, null)));

    FlakyTool tool = new FlakyTool();
    List<AgentTool> tools = List.of(tool);
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    BaseContextBuilder toolContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    HybridEngine engine = new HybridEngine(reasoningModel, toolAssistantModel, tools, reasoningContext, toolContext,
        sessionStore, 2);

    CapturingListener listener = new CapturingListener();
    engine.registerListener("session", listener);

    Message result = engine.invoke(sessionId, Message.user("hello"));

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolRepairs).isGreaterThanOrEqualTo(1);
    assertThat(listener.toolExecutions).isNotEmpty();
    assertThat(tool.invocations).isGreaterThanOrEqualTo(2);

    List<Message> toolMessages = sessionStore.getMessages(sessionId + "_tool");
    assertThat(toolMessages).anyMatch(message -> message.getRole() == Role.SYSTEM);
  }

  @Test
  void invokeReturnsNullWhenInvocationLimitReached() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    Message empty = new Message(Role.ASSISTANT, "", null, null, null);
    LLMModel reasoningModel = new QueueModel(ResponseFormatType.TEXT, List.of(empty, empty, empty));
    LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.TEXT,
        List.of(new Message(Role.ASSISTANT, "", null, null, null)));

    List<AgentTool> tools = List.of();
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    BaseContextBuilder toolContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    HybridEngine engine = new HybridEngine(reasoningModel, toolAssistantModel, tools, reasoningContext, toolContext,
        sessionStore, 1);

    Message result = engine.invoke(sessionId, Message.user("hello"));

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
    BaseContextBuilder toolContext = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
    HybridEngine engine = new HybridEngine(reasoningModel, toolAssistantModel, List.of(), reasoningContext, toolContext,
        sessionStore, 2);

    CapturingListener listener = new CapturingListener();
    engine.registerListener("session", listener);

    Message result = engine.invoke(sessionId, Message.user("hello"));

    assertThat(result.getContent()).isEqualTo("done");
    assertThat(listener.toolExecutions).isEmpty();
    List<Message> reasoningMessages = sessionStore.getMessages(sessionId + "_reasoning");
    assertThat(reasoningMessages).anyMatch(message -> message.getRole() == Role.USER
        && message.getContent().contains("Unknown tool"));
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

    List<AgentTool> tools = List.of(new EchoTool());
    BaseContextBuilder reasoningContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    BaseContextBuilder toolContext = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
    HybridEngine engine = new HybridEngine(reasoningModel, toolAssistantModel, tools, reasoningContext, toolContext,
        sessionStore, 2);

    Message result = engine.invoke(sessionId, Message.user("hello"));

    assertThat(result.getContent()).isEqualTo("done");
    List<Message> toolMessages = sessionStore.getMessages(sessionId + "_tool");
    assertThat(toolMessages).anyMatch(message -> message.getRole() == Role.ASSISTANT);
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
    BaseContextBuilder toolContext = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
    HybridEngine engine = new HybridEngine(reasoningModel, toolAssistantModel, List.of(), reasoningContext, toolContext,
        sessionStore, 2);

    Message result = engine.invoke(sessionId, Message.user("hello"));

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

  private record EchoTool() implements AgentTool {
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

  private static final class FlakyTool implements AgentTool {
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
    private final List<List<ToolCall>> toolPlans = new ArrayList<>();
    private final List<ToolExecution> toolExecutions = new ArrayList<>();
    private int toolRepairs = 0;
    private final List<String> finalAnswers = new ArrayList<>();

    @Override
    public void onToolPlan(final String sessionId, final List<ToolCall> toolCalls) {
      toolPlans.add(toolCalls);
    }

    @Override
    public void onToolExecution(final String sessionId, final ToolExecution toolExecution) {
      toolExecutions.add(toolExecution);
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
