package com.agentengine.engine.agents;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.ParallelAggregationPolicy;
import com.agentengine.engine.api.beans.config.ParallelStoppingPolicy;
import com.agentengine.engine.builders.agent.ParallelOrchestratorAgentBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.util.StringUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.StructuredConcurrencyUtils;
import com.agentengine.engine.utils.StructuredConcurrencyUtils.TaskOutcome;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Policy-aware parallel orchestrator that emits only aggregated orchestrator output.
 *
 * <p>Branch success is strict: a branch is successful only when a terminal event has
 * {@code turnComplete=true}.
 */
public final class ParallelOrchestratorAgent extends Agent {
  private final ParallelAggregationPolicy aggregationPolicy;
  private final ParallelStoppingPolicy stoppingPolicy;
  private final int quorum;
  private final List<BranchSpec> branchSpecs;
  private final int requiredSuccesses;

  public ParallelOrchestratorAgent(final ParallelOrchestratorAgentBuilder builder) {
    super(builder);
    this.aggregationPolicy = normalizeAggregationPolicy(builder.aggregationPolicy());
    this.stoppingPolicy = normalizeStoppingPolicy(builder.stoppingPolicy());
    this.quorum = Math.max(1, builder.quorum());
    this.branchSpecs = buildBranchSpecs();
    this.requiredSuccesses = requiredSuccesses(branchSpecs.size());
  }

  @Override
  protected Flowable<Event> runLiveImpl(final InvocationContext invocationContext) {
    return runAsyncImpl(invocationContext);
  }

  @Override
  protected Flowable<Event> runAsyncImpl(final InvocationContext invocationContext) {
    final Result result = executeBranches(invocationContext);
    if (result.fallbackUsed()) {
      RunStateUtils.getState(invocationContext)
          .addViolation(
              Violation.builder(ParallelOrchestrationConstants.ViolationCode.POLICY_FALLBACK)
                  .message(ParallelOrchestrationConstants.Message.FALLBACK)
                  .correctionMessage(ParallelOrchestrationConstants.Message.FALLBACK)
                  .details(
                      Map.of(
                          ParallelOrchestrationConstants.DetailKey.STOPPING_POLICY,
                          stoppingPolicy.name(),
                          ParallelOrchestrationConstants.DetailKey.AGGREGATION_POLICY,
                          aggregationPolicy.name(),
                          ParallelOrchestrationConstants.DetailKey.REQUIRED_SUCCESSES,
                          result.requiredSuccesses(),
                          ParallelOrchestrationConstants.DetailKey.SUCCESS_COUNT,
                          result.successful(),
                          ParallelOrchestrationConstants.DetailKey.COMPLETED_COUNT,
                          result.completed()))
                  .build());
    }

    final String text =
        StringUtils.isNotBlank(result.outputText())
            ? result.outputText()
            : ParallelOrchestrationConstants.Message.EMPTY_RESULT;
    final Content content =
        Content.builder().role("model").parts(List.of(Part.fromText(text))).build();
    final Event event =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId(invocationContext.invocationId())
            .author(name())
            .branch(invocationContext.branch())
            .content(content)
            .turnComplete(true)
            .build();
    return Flowable.just(event);
  }

  private Result executeBranches(final InvocationContext invocationContext) {
    final AtomicInteger successes = new AtomicInteger(0);
    final List<Callable<BranchExecution>> tasks = new ArrayList<>(branchSpecs.size());
    for (final BranchSpec branchSpec : branchSpecs) {
      tasks.add(() -> runBranch(invocationContext, branchSpec));
    }

    final List<TaskOutcome<BranchExecution>> outcomes = StructuredConcurrencyUtils.runConcurrentlyUntil(tasks, subtask -> shouldStopSubtasks(subtask, successes, requiredSuccesses));

    final List<BranchExecution> executions = collectExecutions(outcomes);
    final int successful = countSuccessful(executions);
    final boolean fallbackUsed = successful < requiredSuccesses;
    final String outputText = fallbackUsed ? bestEffort(executions) : aggregateByPolicy(executions);
    return new Result(
        outputText, fallbackUsed, requiredSuccesses, successful, countCompleted(executions));
  }

  private List<BranchSpec> buildBranchSpecs() {
    final List<? extends BaseAgent> currentSubAgents = CollectionUtils.nullSafeList(subAgents());
    final List<BranchSpec> branchSpecs = new ArrayList<>(currentSubAgents.size());
    for (int index = 0; index < currentSubAgents.size(); index++) {
      final BaseAgent subAgent = currentSubAgents.get(index);
      if (subAgent == null || StringUtils.isBlank(subAgent.name())) {
        continue;
      }
      branchSpecs.add(new BranchSpec(index, subAgent.name(), subAgent));
    }
    return branchSpecs;
  }

  private boolean shouldStopSubtasks(final Subtask<? extends BranchExecution> subtask, final AtomicInteger successes, final int requiredSuccesses) {
    if (stoppingPolicy == ParallelStoppingPolicy.ALL_COMPLETE || requiredSuccesses <= 0) {
      return false;
    }
    if (subtask.state() != Subtask.State.SUCCESS) {
      return false;
    }
    final BranchExecution execution = subtask.get();
    if (!execution.successful()) {
      return false;
    }
    return successes.incrementAndGet() >= requiredSuccesses;
  }

  private List<BranchExecution> collectExecutions(final List<TaskOutcome<BranchExecution>> outcomes) {
    final List<BranchExecution> executions = new ArrayList<>(branchSpecs.size());
    for (final TaskOutcome<BranchExecution> outcome : CollectionUtils.nullSafeList(outcomes)) {
      final BranchSpec branchSpec = branchSpecs.get(outcome.index());
      switch (outcome.state()) {
        case SUCCESS -> executions.add(outcome.value());
        case FAILED -> executions.add(BranchExecution.failed(branchSpec.order(), branchSpec.subAgentId()));
        case UNAVAILABLE -> executions.add(BranchExecution.unavailable(branchSpec.order(), branchSpec.subAgentId()));
      }
    }
    executions.sort(Comparator.comparingInt(BranchExecution::order));
    return executions;
  }

  private static BranchExecution runBranch(final InvocationContext invocationContext, final BranchSpec branchSpec) {
    try {
      final List<Event> events =
          CollectionUtils.nullSafeList(
              branchSpec.subAgent().runAsync(invocationContext).toList().blockingGet());
      final Event terminalEvent = resolveTerminalEvent(events);
      final boolean successful = terminalEvent != null && terminalEvent.turnComplete().orElse(false);
      final String outputText = extractLatestText(events);
      return BranchExecution.completed(
          branchSpec.order(), branchSpec.subAgentId(), outputText, successful);
    } catch (Exception ignored) {
      return BranchExecution.failed(branchSpec.order(), branchSpec.subAgentId());
    }
  }

  private String aggregateByPolicy(final List<BranchExecution> executions) {
    return switch (aggregationPolicy) {
      case BEST_EFFORT -> bestEffort(executions);
      case MAJORITY_VOTE -> majorityVote(executions);
      case CONCATENATE, UNKNOWN -> concatenate(executions);
    };
  }

  private static String concatenate(final List<BranchExecution> executions) {
    final List<String> parts = successfulTexts(executions);
    if (parts.isEmpty()) {
      return "";
    }
    return String.join("\n\n", parts);
  }

  private static String bestEffort(final List<BranchExecution> executions) {
    final List<BranchExecution> successfulCandidates = successfulExecutions(executions);
    final List<BranchExecution> source =
        successfulCandidates.isEmpty() ? textExecutions(executions) : successfulCandidates;
    return source.stream()
        .filter(execution -> StringUtils.isNotBlank(execution.outputText()))
        .max(
            Comparator.comparingInt((BranchExecution execution) -> execution.outputText().length())
                .thenComparingInt(execution -> -execution.order()))
        .map(BranchExecution::outputText)
        .orElse("");
  }

  private static String majorityVote(final List<BranchExecution> executions) {
    final List<BranchExecution> successfulCandidates = successfulExecutions(executions);
    final List<BranchExecution> source =
        successfulCandidates.isEmpty() ? textExecutions(executions) : successfulCandidates;
    if (source.isEmpty()) {
      return "";
    }
    final Map<String, List<BranchExecution>> grouped = new LinkedHashMap<>();
    for (final BranchExecution execution : source) {
      final String key = normalizeVoteKey(execution.outputText());
      if (StringUtils.isBlank(key)) {
        continue;
      }
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(execution);
    }
    if (grouped.isEmpty()) {
      return "";
    }

    List<BranchExecution> bestGroup = List.of();
    for (final List<BranchExecution> currentGroup : grouped.values()) {
      if (currentGroup.size() > bestGroup.size()) {
        bestGroup = currentGroup;
        continue;
      }
      if (currentGroup.size() == bestGroup.size()
          && bestEffort(currentGroup).length() > bestEffort(bestGroup).length()) {
        bestGroup = currentGroup;
      }
    }
    return bestEffort(bestGroup);
  }

  private static List<String> successfulTexts(final List<BranchExecution> executions) {
    return successfulExecutions(executions).stream()
        .map(BranchExecution::outputText)
        .filter(StringUtils::isNotBlank)
        .toList();
  }

  private static List<BranchExecution> successfulExecutions(final List<BranchExecution> executions) {
    return CollectionUtils.nullSafeList(executions).stream()
        .filter(BranchExecution::successful)
        .filter(execution -> StringUtils.isNotBlank(execution.outputText()))
        .toList();
  }

  private static List<BranchExecution> textExecutions(final List<BranchExecution> executions) {
    return CollectionUtils.nullSafeList(executions).stream()
        .filter(BranchExecution::completed)
        .filter(execution -> StringUtils.isNotBlank(execution.outputText()))
        .toList();
  }

  private int requiredSuccesses(final int branchCount) {
    if (branchCount <= 0) {
      return 0;
    }
    return switch (stoppingPolicy) {
      case FIRST_SUCCESS -> 1;
      case QUORUM -> Math.min(Math.max(1, quorum), branchCount);
      case ALL_COMPLETE, UNKNOWN -> branchCount;
    };
  }

  private static int countSuccessful(final List<BranchExecution> executions) {
    int count = 0;
    for (final BranchExecution execution : CollectionUtils.nullSafeList(executions)) {
      if (execution.successful()) {
        count++;
      }
    }
    return count;
  }

  private static int countCompleted(final List<BranchExecution> executions) {
    int count = 0;
    for (final BranchExecution execution : CollectionUtils.nullSafeList(executions)) {
      if (execution.completed()) {
        count++;
      }
    }
    return count;
  }

  private static Event resolveTerminalEvent(List<Event> events) {
    events = CollectionUtils.nullSafeList(events);
    for (int index = events.size() - 1; index >= 0; index--) {
      final Event event = events.get(index);
      if (event == null) {
        continue;
      }
      if (event.turnComplete().orElse(false)
          || event.finalResponse()
          || event.actions().endInvocation().orElse(false)) {
        return event;
      }
    }
    return events.isEmpty() ? null : events.getLast();
  }

  private static String extractLatestText(final List<Event> events) {
    final List<Event> safeEvents = CollectionUtils.nullSafeList(events);
    for (int index = safeEvents.size() - 1; index >= 0; index--) {
      final Event event = safeEvents.get(index);
      if (event == null || event.content().isEmpty()) {
        continue;
      }
      final String text = event.content().get().text();
      if (StringUtils.isNotBlank(text)) {
        return text.trim();
      }
    }
    return "";
  }

  private static String normalizeVoteKey(final String value) {
    if (StringUtils.isBlank(value)) {
      return "";
    }
    return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static ParallelAggregationPolicy normalizeAggregationPolicy(
      final ParallelAggregationPolicy policy) {
    if (policy == null || policy == ParallelAggregationPolicy.UNKNOWN) {
      return ParallelAggregationPolicy.CONCATENATE;
    }
    return policy;
  }

  private static ParallelStoppingPolicy normalizeStoppingPolicy(final ParallelStoppingPolicy policy) {
    if (policy == null || policy == ParallelStoppingPolicy.UNKNOWN) {
      return ParallelStoppingPolicy.ALL_COMPLETE;
    }
    return policy;
  }

  private record BranchSpec(int order, String subAgentId, BaseAgent subAgent) {}

  private record Result(
      String outputText, boolean fallbackUsed, int requiredSuccesses, int successful, int completed) {}

  private enum BranchStatus {
    COMPLETED,
    FAILED,
    UNAVAILABLE
  }

  private record BranchExecution(
      int order, String subAgentId, String outputText, boolean successful, BranchStatus status) {
    private static BranchExecution completed(
        final int order,
        final String subAgentId,
        final String outputText,
        final boolean successful) {
      return new BranchExecution(
          order, subAgentId, StringUtils.isBlank(outputText) ? "" : outputText.trim(), successful, BranchStatus.COMPLETED);
    }

    private static BranchExecution failed(final int order, final String subAgentId) {
      return new BranchExecution(order, subAgentId, "", false, BranchStatus.FAILED);
    }

    private static BranchExecution unavailable(final int order, final String subAgentId) {
      return new BranchExecution(order, subAgentId, "", false, BranchStatus.UNAVAILABLE);
    }

    private boolean completed() {
      return status != BranchStatus.UNAVAILABLE;
    }
  }
}
