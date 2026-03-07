package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;

/**
 * Injects planning context into the request.
 *
 * <p>Responsibilities:
 * - Add a high-level plan summary to request content.
 * - Inject an active-task focus anchor into content when a task is open.
 * - No-op when no plan is present.
 *
 * <p>Ownership: planning context and task focus injection.
 */
public final class PlanningRequestProcessor implements RequestProcessor {
  public static final PlanningRequestProcessor INSTANCE = new PlanningRequestProcessor();

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final Plan plan = RunStateUtils.getState(context).plan();
    if (plan == null) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    final List<Content> appended = new ArrayList<>();

    // 1. High-level plan summary
    final String summary = PlanningUtils.buildPlanSummary(plan);
    if (StringUtils.isNotBlank(summary)) {
      appended.add(Content.builder().role("user").parts(List.of(Part.fromText(summary))).build());
    }

    // 2. Low-level task focus (structural anchor).
    if (PlanningUtils.hasOpenTask(plan)) {
      final StringBuilder taskPrompt = new StringBuilder(PlanningUtils.buildTaskFocusPrompt(plan));
      if (!taskPrompt.isEmpty()) {
        final String anchorText =
            "### STRUCTURAL ANCHOR (STRICT FOCUS)\n"
                + "Active Task: ["
                + PlanningUtils.getTaskIdValue(PlanningUtils.getOpenTask(plan))
                + "]\n"
                + taskPrompt
                + "\n\nFollow the structural thought protocol and your current plan strictly.";
        appended.add(Content.builder().role("user").parts(List.of(Part.fromText(anchorText))).build());
      }
    }

    if (appended.isEmpty()) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    final LlmRequest updated =
        request.toBuilder().contents(CollectionUtils.append(request.contents(), appended)).build();
    return Single.just(RequestProcessingResult.create(updated, List.of()));
  }
}
