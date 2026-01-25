package com.agentengine.engine;

import static com.agentengine.engine.utils.AgentUtils.parseJsonPayload;
import static java.lang.StringTemplate.STR;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.MessageStoreMark;
import com.agentengine.engine.api.SessionStore;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.PlanItem;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.beans.session.PlanUpdate;
import com.agentengine.engine.api.beans.session.Session;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.exception.AgentException;
import com.agentengine.engine.api.exception.ModelInvocationException;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.ResourceUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.AgentUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HybridAgent implements Agent {
  private static final String UPDATE_PLAN_TOOL_NAME = "update_plan";
  private static final String CLARIFICATION_STATUS = "clarification_required";
  private static final String CLARIFICATION_TOOL_NAME = "user_clarification";
  private static final String MISSING_TOOL_AND_FINAL_MESSAGE = "You must provide either a final answer or an update_plan tool call as defined in protocol.";

  private final LLMModel reasoningModel;
  private final LLMModel routerModel;
  private final ToolExecutor toolExecutor;
  private final int invocationLimit;
  private final String agentId;
  private final SessionStore sessionStore;

  public HybridAgent(final LLMModel reasoningModel, final LLMModel routerModel, final ToolExecutor toolExecutor,
      final SessionStore sessionStore, final int invocationLimit, final String agentId) {
    this.reasoningModel = reasoningModel;
    this.routerModel = routerModel;
    this.toolExecutor = toolExecutor;
    this.sessionStore = sessionStore;
    this.invocationLimit = invocationLimit;
    this.agentId = agentId;
  }

  @Override
    public Message invoke(final String sessionId, final Message message, final AgentListener listener) {
        Session session = loadSession(sessionId);
        final boolean awaitingClarification = session.isAwaitingClarification();
        final String runId = awaitingClarification ? session.getRunId() : UUID.randomUUID().toString();
        if (!awaitingClarification) {
            resetSessionExecution(session, runId);
            reasoningModel.getContextManager().appendMessage(sessionId, runId, Message.user(message.getContent()));
            listener.onRunStarted(sessionId, runId);

            if (shouldGenerateTasks(sessionId, runId) && !session.isTasksGenerated()) {
                final List<PlanItem> planned = generateTaskList(sessionId, runId, message, listener, session, true);
                if (CollectionUtils.isNotEmpty(planned)) {
                    final String note = updatePlan(session, new PlanUpdate("Planning agent", planned));
                    emitPlanUpdateNote(sessionId, runId, note);
                    session.setTasksGenerated(true);
                }
            }
        } else {
            resumeFromClarification(sessionId, runId, message, session, listener);
        }

        Message finalResponse = Message.assistant("");
        int reasoningTurns = 0;
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
                clearSessionExecution(session);
                persistSession(session);
                throw ex;
            } catch (Exception ex) {
                listener.onRunError(sessionId, runId, ex);
                final Message failure = Message.system(STR."Reasoner failed: \{ex.getMessage()}");
                emitFinalAnswer(sessionId, failure, listener);
                listener.onRunFinished(sessionId, runId);
                clearSessionExecution(session);
                persistSession(session);
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
      if (containsPlanToolCalls(toolCalls)) {
        emitPlanToolEvents(sessionId, toolCalls, listener);
        reasoningModel.getContextManager().appendMessage(sessionId, runId,
            Message.system("Planning updates come from the planning tool, not the reasoning model."));
        toolCalls = filterOutPlanToolCalls(toolCalls);
      }

            List<ToolExecution> executions;
            if (CollectionUtils.isNotEmpty(toolCalls)) {
                executions = toolExecutor.execute(sessionId, runId, toolCalls, listener);
            } else {
                final List<PlanItem> refreshed = generateTaskList(sessionId, runId, message, listener, session, false);
                if (CollectionUtils.isNotEmpty(refreshed)) {
                    final String note = updatePlan(session, new PlanUpdate("Planning agent", refreshed));
                    emitPlanUpdateNote(sessionId, runId, note);
                }
                if (hasPendingPlan(session)) {
                    executions = executePlanItem(sessionId, runId, selectPlanItemForWork(session), listener);
                } else {
                    reasoningModel.getContextManager().appendMessage(sessionId, runId,
                            Message.system(MISSING_TOOL_AND_FINAL_MESSAGE));
                    continue;
                }
            }

            final ToolExecution clarification = findClarification(executions);
            if (clarification != null) {
                session.setAwaitingClarification(true);
                session.setPendingClarification(clarification.getToolCall());
                session.setRunId(runId);
                persistSession(session);
                return Message.assistant("");
            }
            appendToolResults(sessionId, runId, executions);
        } while (reasoningTurns < invocationLimit);

        if (StringUtils.isBlank(finalResponse.getContent()) && reasoningTurns >= invocationLimit) {
            finalResponse = Message.assistant(STR."Number of assistant invocations exceeded maximum : \{invocationLimit}");
        }

        listener.onRunFinished(sessionId, runId);
        persistSession(clearSessionExecution(session));
        return finalResponse;
    }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return reasoningModel.getContextManager().buildPrompt(sessionId);
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
        .nullSafeMutableList(reasoningModel.getContextManager().buildPrompt(sessionId));
    Message response;
    try {
      response = reasoningModel.generate(prompt);
    } catch (Exception e) {
      throw new ModelInvocationException("reasoning-model", "Failed to generate reasoning response", e);
    }
    response = AgentUtils.sanitizeMessage(response, reasoningModel.responseFormat(), reasoningModel.thoughtsEnabled(),
        reasoningModel.thoughtsStartTag(), reasoningModel.thoughtsEndTag());
    reasoningModel.getContextManager().appendMessage(sessionId, runId, response);

    final String repairMessage = AgentUtils.getRepairMessageIfInvalid(response);
    if (StringUtils.isBlank(repairMessage)) {
      return response;
    }
    reasoningModel.getContextManager().appendMessage(sessionId, runId, Message.system(repairMessage));

    if (maxRetries == 0) {
      return Message.assistant("");
    }
    return runReasonerWithRepair(sessionId, runId, maxRetries - 1);
  }

  private static List<ToolCall> filterOutPlanToolCalls(final List<ToolCall> toolCalls) {
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

  private boolean containsPlanToolCalls(final List<ToolCall> toolCalls) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      return false;
    }
    for (ToolCall call : toolCalls) {
      if (call != null && UPDATE_PLAN_TOOL_NAME.equalsIgnoreCase(call.name())) {
        return true;
      }
    }
    return false;
  }

  private void emitPlanToolEvents(final String sessionId, final List<ToolCall> toolCalls,
      final AgentListener listener) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      return;
    }
    for (ToolCall call : toolCalls) {
      if (call == null || call.name() == null) {
        continue;
      }
      if (!UPDATE_PLAN_TOOL_NAME.equalsIgnoreCase(call.name())) {
        continue;
      }
      listener.onToolCallStart(sessionId, call.id(), call.name());
      if (call.args() != null) {
        listener.onToolCallArgs(sessionId, call.id(), JsonUtils.toJson(call.args()));
      }
      listener.onToolCallEnd(sessionId, call.id());
    }
  }

  private List<ToolExecution> executePlanItem(final String sessionId, final String runId, final PlanItem planItem,
                                                final AgentListener listener) {
        if (planItem == null) {
            return List.of();
        }

        // Instead of using a separate agent, we'll guide the main agent to execute the plan step
        // by appending a system message that indicates the current plan step
        Message planStepMessage = Message.system(STR."Current plan step: \{planItem.step()}");
        reasoningModel.getContextManager().appendMessage(sessionId, runId, planStepMessage);

        // The main agent will then naturally generate appropriate tool calls based on the plan step
        // and its available tools, which will be processed in the main loop
        return List.of(); // Return empty list since the actual execution happens in the main loop
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
    reasoningModel.getContextManager().appendMessage(sessionId, runId,
        Message.user(ToolUtils.formatToolExecutions(executions)));
  }

  private ToolExecution findClarification(final List<ToolExecution> executions) {
    if (CollectionUtils.isEmpty(executions)) {
      return null;
    }
    for (ToolExecution execution : executions) {
      if (execution != null && CLARIFICATION_STATUS.equals(execution.getStatus()) && execution.getToolCall() != null
          && CLARIFICATION_TOOL_NAME.equalsIgnoreCase(execution.getToolCall().name())) {
        return execution;
      }
    }
    return null;
  }

  private void resumeFromClarification(final String sessionId, final String runId, final Message message,
      final Session session, final AgentListener listener) {
    final ToolCall pendingCall = session.getPendingClarification();
    if (pendingCall == null) {
      session.setAwaitingClarification(false);
      session.setPendingClarification(null);
      return;
    }
    final ToolExecution execution = new ToolExecution(pendingCall, "ok", message == null ? "" : message.getContent(),
        null, 0);
    execution.setId(UUID.randomUUID().toString().replaceAll("-", ""));
    listener.onToolCallResult(sessionId, pendingCall.id(), execution.getOutput());
    appendToolResults(sessionId, runId, List.of(execution));
    session.setAwaitingClarification(false);
    session.setPendingClarification(null);
  }

  private boolean shouldGenerateTasks(final String sessionId, final String runId) {
    final String promptString = ResourceUtils.loadResourceAsString("/prompts/hybrid/complexity_router.txt");
    final ContextManager contextManager = routerModel.getContextManager();
    final MessageStoreMark mark = contextManager.mark(sessionId);
    try {
      contextManager.appendMessage(sessionId, runId, Message.system(promptString));
      final List<Message> prompt = contextManager.buildPrompt(sessionId);
      final Message response = routerModel.generate(prompt);
      final Map<String, Object> payload = parseJsonPayload(response == null ? null : response.getContent());
      final Boolean isComplex = CollectionUtils.getBooleanValueFromMap(payload, "complex");
      return isComplex != null && isComplex;
    } finally {
      contextManager.reset(sessionId, mark);
    }
  }

  private List<PlanItem> generateTaskList(final String sessionId, final String runId, final Message message,
      final AgentListener listener, final Session session, final boolean initial) {
    final List<ToolExecution> executions = runPlanningTool(sessionId, runId, message, listener, session, initial);
    if (CollectionUtils.isEmpty(executions)) {
      return List.of();
    }
    return AgentUtils.parsePlanItemsFromText(executions.getFirst().getOutput());
  }

  private Session loadSession(final String sessionId) {
    Session session = sessionStore.findById(sessionId);
    if (session == null) {
      session = new Session();
      session.setId(sessionId);
      session.setAgentId(agentId);
    } else if (StringUtils.isBlank(session.getAgentId())) {
      session.setAgentId(agentId);
    }
    return session;
  }

  private static void resetSessionExecution(final Session session, final String runId) {
    session.setRunId(runId);
    session.setAwaitingClarification(false);
    session.setPendingClarification(null);
    session.setTasksGenerated(false);
    session.setPendingPlan(List.of());
    session.setActivePlanItem(null);
  }

  private Session clearSessionExecution(final Session session) {
    session.setRunId(null);
    session.setAwaitingClarification(false);
    session.setPendingClarification(null);
    session.setTasksGenerated(false);
    session.setPendingPlan(List.of());
    session.setActivePlanItem(null);
    return session;
  }

  private String updatePlan(final Session session, final PlanUpdate update) {
    if (session == null || update == null || CollectionUtils.isEmpty(update.plan())) {
      session.setPendingPlan(List.of());
      session.setActivePlanItem(null);
      return null;
    }
    final List<PlanItem> ordered = new ArrayList<>();
    for (PlanItem item : update.plan()) {
      if (item == null || StringUtils.isBlank(item.step())) {
        continue;
      }
      if (item.status() == PlanStatus.COMPLETED) {
        continue;
      }
      ordered.add(item.status() == null ? item.withStatus(PlanStatus.PENDING) : item);
    }
    if (ordered.isEmpty()) {
      session.setPendingPlan(List.of());
      session.setActivePlanItem(null);
      return null;
    }
    final PlanItem previousActive = session.getActivePlanItem();
    PlanItem activeCandidate = null;
    if (previousActive != null && StringUtils.isNotBlank(previousActive.step())) {
      for (PlanItem item : ordered) {
        if (item != null && samePlanItem(previousActive, item)) {
          activeCandidate = item;
          break;
        }
      }
    }
    if (activeCandidate == null) {
      activeCandidate = ordered.getFirst();
    }
    final String note = buildActivePlanUpdateNote(previousActive, activeCandidate);
    final List<PlanItem> pending = new ArrayList<>();
    if (activeCandidate != null) {
      pending.add(activeCandidate);
      for (PlanItem item : ordered) {
        if (item == null || samePlanItem(activeCandidate, item)) {
          continue;
        }
        pending.add(item);
      }
    }
    session.setPendingPlan(pending);
    session.setActivePlanItem(activeCandidate);
    return note;
  }

  private List<ToolExecution> runPlanningTool(final String sessionId, final String runId, final Message message,
      final AgentListener listener, final Session session, final boolean initial) {
    final Map<String, Object> args = new java.util.HashMap<>();
    args.put("message", message == null ? "" : message.getContent());
    args.put("mode", initial ? "initial" : "update");
    args.put("current_plan", planArgs(session));
    if (session != null && session.getActivePlanItem() != null) {
      args.put("active_step", session.getActivePlanItem().step());
      args.put("active_step_id", session.getActivePlanItem().id());
    }
    final ToolCall call = new ToolCall(UUID.randomUUID().toString(), PlanningAgent.NAME, args);
    final List<ToolExecution> executions = toolExecutor.execute(sessionId, runId, List.of(call), listener);
    appendToolResults(sessionId, runId, executions);
    return executions;
  }

  private List<Map<String, Object>> planArgs(final Session session) {
    if (session == null || CollectionUtils.isEmpty(session.getPendingPlan())) {
      return List.of();
    }
    return session.getPendingPlan().stream().map(item -> {
      final Map<String, Object> entry = new java.util.HashMap<>();
      entry.put("id", item.id());
      entry.put("step", item.step());
      entry.put("status", item.status().name().toLowerCase());
      return entry;
    }).toList();
  }

  private void emitPlanUpdateNote(final String sessionId, final String runId, final String note) {
    if (StringUtils.isBlank(note)) {
      return;
    }
    reasoningModel.getContextManager().appendMessage(sessionId, runId, Message.system(note));
  }

  private boolean samePlanItem(final PlanItem left, final PlanItem right) {
    if (left == null || right == null) {
      return false;
    }
    if (StringUtils.isNotBlank(left.id()) && StringUtils.isNotBlank(right.id())) {
      return left.id().equalsIgnoreCase(right.id());
    }
    return left.step().equalsIgnoreCase(right.step());
  }

  private String buildActivePlanUpdateNote(final PlanItem previous, final PlanItem active) {
        if (previous == null || active == null) {
            return null;
        }
        if (StringUtils.isNotBlank(previous.id()) && StringUtils.isNotBlank(active.id())
            && previous.id().equalsIgnoreCase(active.id())
            && !previous.step().equalsIgnoreCase(active.step())) {
            return STR."Active plan item updated from \"\{previous.step()}\" to \"\{active.step()}\".";
        }
        return null;
    }

  private boolean hasPendingPlan(final Session session) {
    return CollectionUtils.isNotEmpty(session.getPendingPlan());
  }

  private PlanItem selectPlanItemForWork(final Session session) {
    if (session == null) {
      return null;
    }
    final List<PlanItem> pending = session.getPendingPlan();
    if (CollectionUtils.isEmpty(pending)) {
      session.setActivePlanItem(null);
      return null;
    }
    final PlanItem active = session.getActivePlanItem();
    if (active != null && StringUtils.isNotBlank(active.step())) {
      for (PlanItem item : pending) {
        if (item != null && samePlanItem(active, item)) {
          session.setActivePlanItem(item);
          return item;
        }
      }
    }
    final PlanItem next = pending.getFirst();
    session.setActivePlanItem(next);
    return next;
  }

  private void persistSession(final Session session) {
    sessionStore.createOrUpdate(session);
  }
}
