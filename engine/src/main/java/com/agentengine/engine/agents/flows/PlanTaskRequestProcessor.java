package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

public final class PlanTaskRequestProcessor implements RequestProcessor {

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final Plan plan = PlanningUtils.getPlanFromContext(context);
    if (!PlanningUtils.hasOpenTask(plan)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    final StringBuilder taskPrompt = new StringBuilder(PlanningUtils.buildTaskFocusPrompt(plan));
    if (taskPrompt.isEmpty()) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    if (PlanningUtils.isNudgeRequired(context)) {
      taskPrompt.append("\nSystem Reminder: Task [").append(PlanningUtils.getTaskIdValue(PlanningUtils.getOpenTask(plan))).append("] is still IN_PROGRESS. Please complete the work or update the task status before concluding.");
      PlanningUtils.setNudgeRequired(context, false);
    }

    String anchorText = "### STRUCTURAL ANCHOR (STRICT FOCUS)\n"
        + "Active Task: ["
        + PlanningUtils.getTaskIdValue(PlanningUtils.getOpenTask(plan))
        + "]\n"
        + taskPrompt;

    final String violation = PlanningUtils.getViolationMessage(context);
    if (StringUtils.isNotBlank(violation)) {
        anchorText += "\n\n⚠️ REJECTION NOTICE:\n" + violation + "\n\nPlease correct the above errors immediately.";
        PlanningUtils.clearViolationMessage(context);
    } else {
        anchorText += "\n\nFollow the structural thought protocol and your current plan strictly.";
    }

    final LlmRequest updated =
        request.toBuilder()
            .contents(
                CollectionUtils.append(
                    request.contents(),
                    Content.fromParts(Part.fromText(anchorText))))
            .build();
    return Single.just(RequestProcessingResult.create(updated, List.of()));
  }
}
