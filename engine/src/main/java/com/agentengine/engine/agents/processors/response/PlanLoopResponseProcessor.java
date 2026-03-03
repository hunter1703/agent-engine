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
import java.util.ArrayList;
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

  public PlanLoopResponseProcessor() {
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    final RunState runState = RunStateUtils.getState(context);
    final Plan plan = runState.plan();
    final Content content = response.content().orElse(null);
    final boolean hasTools = content != null && content.parts().orElse(List.of()).stream().anyMatch(p -> p.functionCall().isPresent());
    final boolean hasText = content != null && content.parts().orElse(List.of()).stream().anyMatch(p -> p.text().isPresent() && !p.thought().orElse(false));
    final boolean isFinishing = hasText && !hasTools;
    final String planViolation = PlanningValidator.canSubmitFinalAnswerOrError(plan);
    final boolean planIncomplete = planViolation != null;
    final LlmResponse adjustedResponse = planIncomplete ? markTextPartsAsThoughts(response) : response;

    if (response.partial().orElse(false)) {
      return Single.just(ResponseProcessingResult.create(adjustedResponse, List.of(), Optional.empty()));
    }

    if (!isFinishing) {
      if (plan == null) {
        return Single.just(ResponseProcessingResult.create(adjustedResponse, List.of(), Optional.empty()));
      }
      final Task activeTask = PlanningUtils.getOpenTask(plan);
      if (activeTask == null || ToolUtils.hasToolParts(response)) {
        return Single.just(ResponseProcessingResult.create(adjustedResponse, List.of(), Optional.empty()));
      }
      final String taskId = PlanningUtils.getTaskIdValue(activeTask);
      final String status = PlanningUtils.getTaskStatusValue(activeTask);
      final String msg = "System Reminder: Task [" + taskId + "] is still " + status + ". "
          + "Please complete the work or update the task status before concluding.";
      runState.addViolation(Violation.builder("incomplete_task")
          .message("Task " + taskId + " is still " + status)
          .correctionMessage(msg)
          .build());

      final LlmResponse updatedResponse = adjustedResponse.toBuilder().turnComplete(false).build();
      return Single.just(ResponseProcessingResult.create(updatedResponse, List.of(), Optional.empty()));
    }

    if (planViolation != null) {
      final String toolSummary = ToolUtils.summarizeToolParts(content.parts().orElse(List.of()));
      final String correctionMessage = toolSummary.isBlank()
          ? planViolation
          : planViolation + " Stripped tool parts: " + toolSummary + ".";
      runState.addViolation(Violation.builder("final_answer_validation")
          .message(planViolation)
          .correctionMessage(correctionMessage)
          .build());
      final LlmResponse updated = adjustedResponse.toBuilder().turnComplete(false).build();
      return Single.just(ResponseProcessingResult.create(updated, List.of(), Optional.empty()));
    }

    return Single.just(ResponseProcessingResult.create(adjustedResponse, List.of(), Optional.empty()));
  }

  private static LlmResponse markTextPartsAsThoughts(final LlmResponse response) {
    final Content content = response.content().orElse(null);
    if (content == null) {
      return response;
    }
    final List<Part> parts = content.parts().orElse(List.of());
    if (parts.isEmpty()) {
      return response;
    }
    boolean updated = false;
    final List<Part> converted = new ArrayList<>(parts.size());
    for (final Part part : parts) {
      if (part.text().isPresent() && !part.thought().orElse(false)) {
        converted.add(part.toBuilder().thought(true).build());
        updated = true;
      } else {
        converted.add(part);
      }
    }
    if (!updated) {
      return response;
    }
    return response.toBuilder().content(content.toBuilder().parts(converted).build()).build();
  }

}
