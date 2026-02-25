package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

/**
 * 
 * <p>Handles:
 * 1. High-level Plan Context: Injects plan summary into system instructions.
 * 2. Low-level Task Focus: Injects strict focus anchor into content.
 */
public final class PlanningRequestProcessor implements RequestProcessor {
  public static final PlanningRequestProcessor INSTANCE = new PlanningRequestProcessor();

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    
    final Plan plan = PlanningUtils.getPlanFromContext(context);
    if (plan == null) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    LlmRequest.Builder builder = request.toBuilder();

    // 1. High-level Plan Summary
    final String summary = PlanningUtils.buildPlanSummary(plan);
    if (StringUtils.isNotBlank(summary)) {
      builder.appendInstructions(List.of(summary));
    }

    // 2. Low-level Task Focus (Structural Anchor)
    if (PlanningUtils.hasOpenTask(plan)) {
      final StringBuilder taskPrompt = new StringBuilder(PlanningUtils.buildTaskFocusPrompt(plan));
      if (!taskPrompt.isEmpty()) {
        String anchorText = "### STRUCTURAL ANCHOR (STRICT FOCUS)\n"
            + "Active Task: ["
            + PlanningUtils.getTaskIdValue(PlanningUtils.getOpenTask(plan))
            + "]\n"
            + taskPrompt
            + "\n\nFollow the structural thought protocol and your current plan strictly.";

        builder.contents(
            CollectionUtils.append(
                request.contents(),
                Content.fromParts(Part.fromText(anchorText))));
      }
    }

    return Single.just(RequestProcessingResult.create(builder.build(), List.of()));
  }
}
