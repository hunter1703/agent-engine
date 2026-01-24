package com.agentengine.engine;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.agents.ToolAssistantAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.PlanItem;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.beans.session.PlanUpdate;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.exception.AgentException;
import com.agentengine.engine.api.exception.ModelInvocationException;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.ResourceUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.ToolOutputFormatter;
import com.agentengine.engine.utils.EngineUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HybridAgent implements Agent {
  private static final String ROUTER_SESSION_SUFFIX = "_router";
  private static final String REASONING_SESSION_SUFFIX = "_reasoning";
  private static final String TOOL_SESSION_SUFFIX = "_tool";
  private static final String UPDATE_PLAN_TOOL_NAME = "update_plan";
  private static final String CLARIFICATION_STATUS = "clarification_required";
  private static final String CLARIFICATION_TOOL_NAME = "user_clarification";
  private static final String MISSING_TOOL_AND_FINAL_MESSAGE =
      "You must provide either a final answer or an update_plan tool call as defined in protocol.";

  private final LLMModel reasoningModel;
  private final LLMModel routerModel;
  private final PlanningAgent planningAgent;
  private final ToolAssistantAgent toolAssistantAgent;
  private final ToolExecutor toolExecutor;
  private final ContextManager contextManager;
  private final int invocationLimit;
  private final Map<String, RunState> runStates = new ConcurrentHashMap<>();

  public HybridAgent(final Dependencies dependencies) {
    this(dependencies.reasoningModel(), dependencies.routerModel(), dependencies.planningAgent(),
        dependencies.toolAssistantAgent(), dependencies.toolExecutor(), dependencies.reasoningContextManager(),
        dependencies.invocationLimit());
  }

  public HybridAgent(final LLMModel reasoningModel, final LLMModel routerModel, final PlanningAgent planningAgent,
      final ToolAssistantAgent toolAssistantAgent, final ToolExecutor toolExecutor, final ContextManager contextManager,
      final int invocationLimit) {
    this.reasoningModel = reasoningModel;
    this.routerModel = routerModel;
    this.planningAgent = planningAgent;
    this.toolAssistantAgent = toolAssistantAgent;
    this.toolExecutor = toolExecutor;
    this.contextManager = contextManager;
    this.invocationLimit = invocationLimit;
  }

  @Override
  public Message invoke(final String sessionId, final Message message, final AgentListener listener) {
    RunState runState = runStates.get(sessionId);
    final boolean awaitingClarification = runState != null && runState.awaitingClarification();
    final String runId = awaitingClarification ? runState.runId() : UUID.randomUUID().toString();
    if (!awaitingClarification) {
      listener.onRunStarted(sessionId, runId);
      appendUserMessage(getReasoningSessionId(sessionId), runId, message);
      appendUserMessage(getToolSessionId(sessionId), runId, message);
      runState = new RunState(runId, new PlanState(), false, false, null);
      runStates.put(sessionId, runState);
      if (shouldGenerateTasks(sessionId, runId, message)) {
        generateTaskList(sessionId, runId, message, listener, runState);
      }
    } else {
      runState = resumeFromClarification(sessionId, runId, message, runState, listener);
      runStates.put(sessionId, runState);
    }

    Message finalResponse = Message.assistant("");
    int reasoningTurns = 0;
    final PlanState planState = runState.planState();
    do {
      final Message result;
      try {
        reasoningTurns++;
        final String stepName = STR."Reasoning Turn \{reasoningTurns}";
        listener.onStepStarted(sessionId, stepName);
        result = runReasoner(sessionId, runId, listener);
        listener.onStepFinished(sessionId, stepName);
      } catch (AgentException ex) {
        listener.onRunError(sessionId, runId, ex);
        runStates.remove(sessionId);
        throw ex;
      } catch (Exception ex) {
        listener.onRunError(sessionId, runId, ex);
        final Message failure = Message.system(STR."Reasoner failed: \{ex.getMessage()}");
        emitFinalAnswer(sessionId, failure, listener);
        listener.onRunFinished(sessionId, runId);
        runStates.remove(sessionId);
        return failure;
      }

      if (result == null) {
        break;
      }

      final String finalAnswer = result.getContent();
      if (StringUtils.isNotBlank(finalAnswer)) {
        emitFinalAnswer(sessionId, result, listener);
        finalResponse = result;
        break;
      }

      List<ToolCall> toolCalls = CollectionUtils.nullSafeMutableList(result.getToolCalls());
      final PlanUpdate planUpdate = EngineUtils.parsePlanUpdate(toolCalls);
      if (planUpdate != null) {
        planState.applyUpdate(planUpdate);
        listener.onPlanUpdate(sessionId, planUpdate);
        toolCalls = filterPlanToolCalls(toolCalls);
      }

      List<ToolExecution> executions;
      if (CollectionUtils.isNotEmpty(toolCalls)) {
        executions = toolExecutor.execute(sessionId, runId, toolCalls, listener);
      } else if (planState.hasPendingSteps()) {
        executions = executePlanItem(sessionId, runId, planState.nextStep(), listener);
      } else {
        contextManager.appendMessage(getReasoningSessionId(sessionId), runId,
            Message.system(MISSING_TOOL_AND_FINAL_MESSAGE));
        if (StringUtils.isBlank(result.getContent())) {
          break;
        }
        continue;
      }

      final ToolExecution clarification = findClarification(executions);
      if (clarification != null) {
        runStates.put(sessionId, runState.withClarification(clarification.getToolCall()));
        return Message.assistant("");
      }
      appendToolResults(sessionId, runId, executions);
    } while (reasoningTurns < invocationLimit);

    if (StringUtils.isBlank(finalResponse.getContent()) && reasoningTurns >= invocationLimit) {
      finalResponse = Message.assistant(STR."Number of assistant invocations exceeded maximum : \{invocationLimit}");
    }

    listener.onRunFinished(sessionId, runId);
    runStates.remove(sessionId);
    return finalResponse;
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return contextManager.buildPrompt(getReasoningSessionId(sessionId));
  }

  private Message runReasoner(final String sessionId, final String runId, final AgentListener listener) {
    Message message = runReasonerWithRepair(sessionId, runId, 5);
    if (message != null && StringUtils.isNotBlank(message.getThoughts())) {
      final String messageId = UUID.randomUUID().toString();
      listener.onThinkingMessageStart(sessionId, messageId, "assistant");
      listener.onThinkingMessageDelta(sessionId, messageId, message.getThoughts());
      listener.onThinkingMessageEnd(sessionId, messageId);
    }
    return message;
  }

  private Message runReasonerWithRepair(final String sessionId, final String runId, final int maxRetries) {
    final List<Message> prompt = CollectionUtils
        .nullSafeMutableList(contextManager.buildPrompt(getReasoningSessionId(sessionId)));
    Message response;
    try {
      response = reasoningModel.generate(prompt);
    } catch (Exception e) {
      throw new ModelInvocationException("reasoning-model", "Failed to generate reasoning response", e);
    }
    response = EngineUtils.sanitizeMessage(response, reasoningModel.responseFormat(), reasoningModel.thoughtsEnabled(),
        reasoningModel.thoughtsStartTag(), reasoningModel.thoughtsEndTag());
    contextManager.appendMessage(getReasoningSessionId(sessionId), runId, response);

    final String repairMessage = EngineUtils.getRepairMessageIfInvalid(response);
    if (StringUtils.isBlank(repairMessage)) {
      return response;
    }
    contextManager.appendMessage(getReasoningSessionId(sessionId), runId, Message.system(repairMessage));

    if (maxRetries == 0) {
      return Message.assistant("");
    }
    return runReasonerWithRepair(sessionId, runId, maxRetries - 1);
  }

  private static List<ToolCall> filterPlanToolCalls(final List<ToolCall> toolCalls) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      return List.of();
    }
    final List<ToolCall> filtered = new ArrayList<>();
    for (ToolCall call : toolCalls) {
      if (call == null || call.name() == null) {
        continue;
      }
      if (!UPDATE_PLAN_TOOL_NAME.equalsIgnoreCase(call.name())) {
        filtered.add(call);
      }
    }
    return filtered;
  }

  private List<ToolExecution> executePlanItem(final String sessionId, final String runId, final PlanItem planItem,
      final AgentListener listener) {
    if (planItem == null) {
      return List.of();
    }
    if (toolAssistantAgent == null) {
      return List.of();
    }
    List<ToolCall> toolCalls = toolAssistantAgent.generateToolCalls(sessionId, runId, planItem, listener);
    List<ToolExecution> executions = toolExecutor.execute(sessionId, runId, toolCalls, listener);

    if (CollectionUtils.isEmpty(executions)) {
      contextManager.appendMessage(getReasoningSessionId(sessionId), runId,
          Message.system(STR."Unable to execute plan item \"\{planItem.step()}\". Please try alternate approach"));
      return List.of();
    }
    return executions;
  }

  private void appendUserMessage(final String sessionId, final String runId, final Message message) {
    if (message == null) {
      return;
    }
    contextManager.appendMessage(sessionId, runId, Message.user(message.getContent()));
  }

  private void emitFinalAnswer(final String sessionId, final Message message, final AgentListener listener) {
    final String messageId = UUID.randomUUID().toString();
    listener.onTextMessageStart(sessionId, messageId, "assistant");
    listener.onTextMessageDelta(sessionId, messageId, message.getContent());
    listener.onTextMessageEnd(sessionId, messageId);
  }

  private void appendToolResults(final String sessionId, final String runId, final List<ToolExecution> executions) {
    if (CollectionUtils.isEmpty(executions)) {
      return;
    }
    contextManager.appendMessage(getReasoningSessionId(sessionId), runId,
        Message.user(ToolOutputFormatter.format(executions)));
  }

  private ToolExecution findClarification(final List<ToolExecution> executions) {
    if (CollectionUtils.isEmpty(executions)) {
      return null;
    }
    for (ToolExecution execution : executions) {
      if (execution != null && CLARIFICATION_STATUS.equals(execution.getStatus())
          && execution.getToolCall() != null
          && CLARIFICATION_TOOL_NAME.equalsIgnoreCase(execution.getToolCall().name())) {
        return execution;
      }
    }
    return null;
  }

  private RunState resumeFromClarification(final String sessionId, final String runId, final Message message,
      final RunState runState, final AgentListener listener) {
    final ToolCall pendingCall = runState.pendingClarification();
    if (pendingCall == null) {
      return runState.clearClarification();
    }
    final ToolExecution execution = new ToolExecution(pendingCall, "ok",
        message == null ? "" : message.getContent(), null, 0);
    execution.setId(UUID.randomUUID().toString().replaceAll("-", ""));
    listener.onToolCallResult(sessionId, pendingCall.id(), execution.getOutput());
    appendToolResults(sessionId, runId, List.of(execution));
    return runState.clearClarification();
  }

  private boolean shouldGenerateTasks(final String sessionId, final String runId, final Message message) {
    if (planningAgent == null || routerModel == null) {
      return false;
    }
    final String prompt = ResourceUtils.loadResourceAsString("/prompts/hybrid/complexity_router.txt");
    final String content = message == null || message.getContent() == null ? "" : message.getContent();
    final List<Message> routerPrompt = List.of(Message.system(prompt), Message.user(content));
    final Message response = routerModel.generate(routerPrompt);
    contextManager.appendMessage(getRouterSessionId(sessionId), runId, Message.user(content));
    if (response != null) {
      contextManager.appendMessage(getRouterSessionId(sessionId), runId, response);
    }
    final String responseContent = response == null ? "" : response.getContent();
    final Map<String, Object> payload = JsonUtils.fromJson(responseContent, Map.class);
    if (payload != null && payload.get("complex") instanceof Boolean complex) {
      return complex;
    }
    return responseContent.toLowerCase().contains("true");
  }

  private void generateTaskList(final String sessionId, final String runId, final Message message,
      final AgentListener listener, final RunState runState) {
    if (runState.tasksGenerated()) {
      return;
    }
    final String toolCallId = UUID.randomUUID().toString();
    listener.onToolCallStart(sessionId, toolCallId, planningAgent.name());
    final Map<String, Object> args = Map.of("message", message == null ? "" : message.getContent());
    listener.onToolCallArgs(sessionId, toolCallId, JsonUtils.toJson(args));
    listener.onToolCallEnd(sessionId, toolCallId);
    final ToolResult result = planningAgent.executeWithContext(new ToolContext(sessionId), args);
    listener.onToolCallResult(sessionId, toolCallId, result.output());
    ToolCall call = new ToolCall(toolCallId, planningAgent.name(), args);
    ToolExecution execution = new ToolExecution(call, result.status(), result.output(), null, 0);
    execution.setId(UUID.randomUUID().toString().replaceAll("-", ""));
    appendToolResults(sessionId, runId, List.of(execution));
    runStates.put(sessionId, runState.withTasksGenerated());
  }

  private static String getReasoningSessionId(final String sessionId) {
    return sessionId + REASONING_SESSION_SUFFIX;
  }

  private static String getToolSessionId(final String sessionId) {
    return sessionId + TOOL_SESSION_SUFFIX;
  }

  private static String getRouterSessionId(final String sessionId) {
    return sessionId + ROUTER_SESSION_SUFFIX;
  }

  public record Dependencies(LLMModel routerModel, PlanningAgent planningAgent, LLMModel reasoningModel,
                             ToolAssistantAgent toolAssistantAgent, ToolExecutor toolExecutor,
                             ContextManager reasoningContextManager, int invocationLimit) {
  }

  private record RunState(String runId, PlanState planState, boolean tasksGenerated,
                          boolean awaitingClarification, ToolCall pendingClarification) {

    private RunState withClarification(final ToolCall toolCall) {
      return new RunState(runId, planState, tasksGenerated, true, toolCall);
    }

    private RunState clearClarification() {
      return new RunState(runId, planState, tasksGenerated, false, null);
    }

    private RunState withTasksGenerated() {
      return new RunState(runId, planState, true, awaitingClarification, pendingClarification);
    }
  }

  private static final class PlanState {
    private final Deque<PlanItem> pendingSteps = new ArrayDeque<>();

    private void applyUpdate(final PlanUpdate update) {
      pendingSteps.clear();
      if (update == null || update.plan() == null) {
        return;
      }
      final List<PlanItem> inProgress = new ArrayList<>();
      final List<PlanItem> pending = new ArrayList<>();
      for (PlanItem item : update.plan()) {
        if (item == null || StringUtils.isBlank(item.step())) {
          continue;
        }
        if (item.status() == PlanStatus.COMPLETED) {
          continue;
        }
        if (item.status() == PlanStatus.IN_PROGRESS) {
          inProgress.add(item);
        } else {
          pending.add(item.withStatus(PlanStatus.PENDING));
        }
      }
      pendingSteps.addAll(inProgress);
      pendingSteps.addAll(pending);
    }

    private boolean hasPendingSteps() {
      return !pendingSteps.isEmpty();
    }

    private PlanItem nextStep() {
      return pendingSteps.pollFirst();
    }
  }
}
