package com.agentengine.engine.testing;

import com.agentengine.engine.agents.flows.DefaultFlow;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MockAgent {
  public enum FlowType {
    PLANNING,
    SIMPLE
  }

  private final ScriptedLlm model;
  private final BaseLlmFlow flow;
  private final LlmAgent agent;
  private final InvocationContext context;

  private MockAgent(
      final ScriptedLlm model,
      final BaseLlmFlow flow,
      final LlmAgent agent,
      final InvocationContext context) {
    this.model = Objects.requireNonNull(model, "model");
    this.flow = Objects.requireNonNull(flow, "flow");
    this.agent = Objects.requireNonNull(agent, "agent");
    this.context = Objects.requireNonNull(context, "context");
  }

  public static Builder builder() {
    return new Builder();
  }

  public Flowable<Event> run() {
    return flow.run(context);
  }

  public Flowable<Event> runLive() {
    return flow.runLive(context);
  }

  public ScriptedLlm model() {
    return model;
  }

  public LlmAgent agent() {
    return agent;
  }

  public InvocationContext context() {
    return context;
  }

  public BaseLlmFlow flow() {
    return flow;
  }

  public static LlmResponse responseWithText(final String text) {
    return responseWithText(text, false, true);
  }

  public static LlmResponse responseWithText(
      final String text, final boolean partial, final boolean turnComplete) {
    return responseWithParts(List.of(Part.fromText(text)), partial, turnComplete);
  }

  public static LlmResponse responseWithParts(
      final List<Part> parts, final boolean partial, final boolean turnComplete) {
    final Content content = Content.builder().role("model").parts(parts).build();
    return LlmResponse.builder().content(content).partial(partial).turnComplete(turnComplete).build();
  }

  public static Part textPart(final String text) {
    return Part.builder().text(text).build();
  }

  public static Part thoughtPart(final String text) {
    return Part.builder().text(text).thought(true).build();
  }

  public static Part toolCallPart(
      final String id, final String name, final java.util.Map<String, Object> args) {
    return Part.builder()
        .functionCall(
            com.google.genai.types.FunctionCall.builder()
                .id(id)
                .name(name)
                .args(args == null ? java.util.Map.of() : args)
                .build())
        .build();
  }

  public static Part toolResponsePart(
      final String id, final String name, final java.util.Map<String, Object> response) {
    return Part.builder()
        .functionResponse(
            com.google.genai.types.FunctionResponse.builder()
                .id(id)
                .name(name)
                .response(response == null ? java.util.Map.of() : response)
                .build())
        .build();
  }

  public static final class Builder {
    private final Deque<List<LlmResponse>> responseBatches = new ArrayDeque<>();
    private FlowType flowType = FlowType.PLANNING;
    private int maxSteps = 100;
    private List<RequestProcessor> requestProcessors = List.of();
    private List<ResponseProcessor> responseProcessors = List.of();
    private final List<BaseTool> tools = new ArrayList<>();
    private String agentName = "mock-agent";
    private String sessionId = "mock-session";
    private String userId = "mock-user";
    private String invocationId = UUID.randomUUID().toString();

    public Builder flowType(final FlowType flowType) {
      this.flowType = flowType == null ? FlowType.PLANNING : flowType;
      return this;
    }

    public Builder maxSteps(final int maxSteps) {
      this.maxSteps = maxSteps;
      return this;
    }

    public Builder requestProcessors(final List<RequestProcessor> requestProcessors) {
      this.requestProcessors = requestProcessors == null ? List.of() : List.copyOf(requestProcessors);
      return this;
    }

    public Builder responseProcessors(final List<ResponseProcessor> responseProcessors) {
      this.responseProcessors = responseProcessors == null ? List.of() : List.copyOf(responseProcessors);
      return this;
    }

    public Builder agentName(final String agentName) {
      if (agentName != null) {
        this.agentName = agentName;
      }
      return this;
    }

    public Builder sessionId(final String sessionId) {
      if (sessionId != null) {
        this.sessionId = sessionId;
      }
      return this;
    }

    public Builder userId(final String userId) {
      if (userId != null) {
        this.userId = userId;
      }
      return this;
    }

    public Builder invocationId(final String invocationId) {
      if (invocationId != null) {
        this.invocationId = invocationId;
      }
      return this;
    }

    public Builder responseBatch(final List<LlmResponse> responses) {
      if (responses != null) {
        responseBatches.addLast(List.copyOf(responses));
      }
      return this;
    }

    public Builder response(final LlmResponse response) {
      if (response != null) {
        responseBatches.addLast(List.of(response));
      }
      return this;
    }

    public Builder tool(final BaseTool tool) {
      if (tool != null) {
        tools.add(tool);
      }
      return this;
    }

    public Builder tools(final List<? extends BaseTool> tools) {
      if (tools != null) {
        this.tools.addAll(tools);
      }
      return this;
    }

    public MockAgent build() {
      final ScriptedLlm model = new ScriptedLlm(agentName, responseBatches);
      final LlmAgent.Builder agentBuilder = LlmAgent.builder().name(agentName).model(model);
      if (!tools.isEmpty()) {
        agentBuilder.tools(tools);
      }
      final LlmAgent agent = agentBuilder.build();
      final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
      final Session session = Session.builder(sessionId)
          .appName("mock")
          .userId(userId)
          .state(state)
          .events(new ArrayList<>())
          .build();
      final InvocationContext context = InvocationContext.builder()
          .agent(agent)
          .session(session)
          .invocationId(invocationId)
          .sessionService(new InMemorySessionService())
          .artifactService(new InMemoryArtifactService())
          .build();
      final com.agentengine.engine.agents.processors.Parser parser = com.agentengine.engine.agents.processors.Parser.builder().withResponseFormat(dev.langchain4j.model.chat.request.ResponseFormatType.JSON).build();
      final List<RequestProcessor> reqProcs = new ArrayList<>();
      reqProcs.add(com.agentengine.engine.agents.processors.request.RunInitRequestProcessor.INSTANCE);
      reqProcs.add(com.agentengine.engine.agents.processors.request.CorrectionProcessor.INSTANCE);
      reqProcs.add(com.agentengine.engine.agents.processors.request.PlanningRequestProcessor.INSTANCE);
      reqProcs.add(com.agentengine.engine.agents.processors.request.FinalAnswerRequestProcessor.INSTANCE);
      List<RequestProcessor> defaultReq = List.of();
      List<ResponseProcessor> defaultRes = List.of();
      try {
        java.lang.reflect.Field reqField = com.google.adk.flows.llmflows.SingleFlow.class.getDeclaredField("REQUEST_PROCESSORS");
        reqField.setAccessible(true);
        defaultReq = (List<RequestProcessor>) reqField.get(null);
        
        java.lang.reflect.Field resField = com.google.adk.flows.llmflows.SingleFlow.class.getDeclaredField("RESPONSE_PROCESSORS");
        resField.setAccessible(true);
        defaultRes = (List<ResponseProcessor>) resField.get(null);
      } catch (Exception e) {
        throw new RuntimeException("Failed to access SingleFlow processors", e);
      }

      com.agentengine.engine.utils.RunStateUtils.initState(context);

      reqProcs.addAll(defaultReq);
      reqProcs.add(parser);
      reqProcs.addAll(requestProcessors);

      final List<ResponseProcessor> resProcs = new ArrayList<>();
      resProcs.add(parser);
      resProcs.add(com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor.INSTANCE);
      resProcs.add(com.agentengine.engine.agents.processors.response.RedundantToolCallsResponseProcessor.INSTANCE);
      resProcs.add(com.agentengine.engine.agents.processors.response.FinalAnswerResponseProcessor.INSTANCE);
      resProcs.addAll(defaultRes);
      // Remove RunCleanupResponseProcessor from MockAgent during tests so state is preserved
      resProcs.removeIf(p -> p instanceof com.agentengine.engine.agents.processors.response.RunCleanupResponseProcessor);

      final BaseLlmFlow flow = new com.agentengine.engine.agents.flows.AbstractFlow(maxSteps, reqProcs, resProcs) {};
      return new MockAgent(model, flow, agent, context);
    }
  }

  public static final class ScriptedLlm extends BaseLlm {
    private final Deque<List<LlmResponse>> responseBatches;
    private final List<LlmRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger callCount = new AtomicInteger();

    private ScriptedLlm(final String modelName, final Deque<List<LlmResponse>> responseBatches) {
      super(modelName);
      this.responseBatches = new ArrayDeque<>(responseBatches);
    }

    @Override
    public Flowable<LlmResponse> generateContent(
        final LlmRequest llmRequest, final boolean stream) {
      requests.add(llmRequest);
      callCount.incrementAndGet();
      final List<LlmResponse> responses = responseBatches.pollFirst();
      if (responses == null) {
        return Flowable.empty();
      }
      return Flowable.fromIterable(responses);
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest llmRequest) {
      return null;
    }

    public int callCount() {
      return callCount.get();
    }

    public List<LlmRequest> requests() {
      synchronized (requests) {
        return List.copyOf(requests);
      }
    }
  }
}
