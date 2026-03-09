package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.PlanningValidator;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Optional;

/**
 * Enforces plan/task completion before the final answer.
 *
 * <p>Responsibilities:
 * - Reject final-answer signals when the plan is incomplete.
 * - Surface active task status violations on non-tool turns.
 * - Strip invalid final-answer signals when plan validation fails.
 *
 * <p>Ownership: plan/task completion validation and enforcement.
 */
public final class PlanLoopResponseProcessor implements ResponseProcessor {
  public static final PlanLoopResponseProcessor INSTANCE = new PlanLoopResponseProcessor();

  private PlanLoopResponseProcessor() {
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final RunState runState = RunStateUtils.getState(context);
    final Plan plan = runState.plan();
    final Content content = response.content().orElse(null);
    final boolean hasTools = content != null && content.parts().orElse(List.of()).stream().anyMatch(p -> p.functionCall().isPresent());
    final boolean hasText = content != null && content.parts().orElse(List.of()).stream().anyMatch(p -> p.text().isPresent() && !p.thought().orElse(false));
    final boolean isFinishing = hasText && !hasTools;

    if (!isFinishing) {
      if (plan == null) {
        return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
      }
      final Task activeTask = PlanningUtils.getOpenTask(plan);
      if (activeTask == null || ToolUtils.hasToolParts(response)) {
        return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
      }
      final String taskId = PlanningUtils.getTaskIdValue(activeTask);
      final String status = PlanningUtils.getTaskStatusValue(activeTask);
      final String msg = "System Reminder: Task [" + taskId + "] is still " + status + ". "
          + "Please complete the work or update the task status before concluding.";
      runState.addViolation(Violation.builder("incomplete_task")
          .message("Task " + taskId + " is still " + status)
          .correctionMessage(msg)
          .build());

      final LlmResponse updatedResponse = response.toBuilder().turnComplete(false).build();
      return Single.just(ResponseProcessingResult.create(updatedResponse, List.of(), Optional.empty()));
    }

    final String planViolation = PlanningValidator.canSubmitFinalAnswerOrError(plan);
    if (planViolation != null) {
      final String toolSummary = ToolUtils.summarizeToolParts(content.parts().orElse(List.of()));
      final String correctionMessage = toolSummary.isBlank()
          ? planViolation
          : planViolation + " Stripped tool parts: " + toolSummary + ".";
      runState.addViolation(Violation.builder("final_answer_validation")
          .message(planViolation)
          .correctionMessage(correctionMessage)
          .build());
      final List<Part> stripped =
          content.parts().orElse(List.of()).stream()
              .map(
                  part ->
                      part.text().isPresent() && !part.thought().orElse(false)
                          ? part.toBuilder().thought(true).build()
                          : part)
              .toList();
      final LlmResponse updated =
          response.toBuilder()
              .content(content.toBuilder().parts(stripped).build())
              .turnComplete(false)
              .build();
      return Single.just(ResponseProcessingResult.create(updated, List.of(), Optional.empty()));
    }

    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }
}
