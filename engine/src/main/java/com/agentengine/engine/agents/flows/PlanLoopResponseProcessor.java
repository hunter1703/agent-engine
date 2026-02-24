package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlanLoopResponseProcessor implements ResponseProcessor {
  private static final String STEP_LIMIT_RESULT =
      "Task abandoned after reaching the step limit (%d steps).";
  private final int maxStepsPerTask;
  private final TaskLoopState taskLoopState;
  private String invocationId;

  public PlanLoopResponseProcessor(final int maxStepsPerTask) {
    this.maxStepsPerTask = maxStepsPerTask;
    this.taskLoopState = new TaskLoopState();
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    final Plan plan = PlanningUtils.getPlanFromContext(context);
    if (!Objects.equals(context.invocationId(), invocationId)) {
        taskLoopState.clear();
        invocationId = context.invocationId();
    }
    if (plan == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }
    if (!PlanningUtils.hasOpenTask(plan)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }
    final Task activeTask = PlanningUtils.getOpenTask(plan);
    if (activeTask == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }
    if (!response.partial().orElse(false)) {
      updateStepCount(context, plan, activeTask);
    }
    if (!PlanningUtils.hasOpenTask(plan)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }
    if (hasToolParts(response)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }
    final LlmResponse updated = response.toBuilder().partial(true).turnComplete(false).build();
    return Single.just(ResponseProcessingResult.create(updated, List.of(), Optional.empty()));
  }

  private static boolean hasToolParts(final LlmResponse response) {
    return response
        .content()
        .map(
            content ->
                content.parts().orElse(List.of()).stream()
                    .anyMatch(p -> p.functionCall().isPresent()))
        .orElse(false);
  }

  private void updateStepCount(final InvocationContext context, final Plan plan, final Task activeTask) {
    if (context == null || maxStepsPerTask <= 0 || activeTask == null) {
      return;
    }
    final String taskKey = resolveTaskKey(plan, activeTask);
    if (StringUtils.isBlank(taskKey)) {
      return;
    }
    final String invocationId = context.invocationId();
    if (StringUtils.isBlank(invocationId)) {
      return;
    }
    final int steps = taskLoopState.increment(taskKey);
    if (steps < maxStepsPerTask) {
      return;
    }
    activeTask.setStatus(TaskStatus.ABANDONED);
    if (StringUtils.isBlank(activeTask.getResult())) {
      activeTask.setResult(STEP_LIMIT_RESULT.formatted(maxStepsPerTask));
    }
    PlanningUtils.savePlan(context, plan);
  }

  private static String resolveTaskKey(final Plan plan, final Task task) {
    if (task == null) {
      return null;
    }
    return plan.getPlanId() + ":" + task.getTaskId();
  }

  private static class TaskLoopState {
    private final ConcurrentMap<String, Integer> stepsByTask = new ConcurrentHashMap<>();

    int increment(final String taskKey) {
      return stepsByTask.merge(taskKey, 1, Integer::sum);
    }

    void clear(){
        stepsByTask.clear();
    }
  }

}
