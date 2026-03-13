package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.util.common.CollectionUtils;
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
 * <p>
 * Responsibilities: - Add a high-level plan summary to request content. -
 * Inject an active-task focus anchor into content when a task is open. - No-op
 * when no plan is present.
 *
 * <p>
 * Ownership: planning context and task focus injection.
 */
public final class PlanningRequestProcessor implements RequestProcessor {
  public static final PlanningRequestProcessor INSTANCE = new PlanningRequestProcessor();

  private PlanningRequestProcessor() {
  }

  @Override
  public Single<RequestProcessingResult> processRequest(final InvocationContext context, final LlmRequest request) {
    final Plan plan = RunUtils.getState(context).plan();
    if (plan == null || plan.getStatus().isTerminal()) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    final List<Content> appended = new ArrayList<>();
    // 1. High-level plan summary
    final String summary = PlanningUtils.buildPlanSummary(plan);
    appended.add(Content.builder().role("user").parts(List.of(Part.fromText(summary))).build());

    // 2. Low-level task focus (structural anchor).
    final String taskPrompt = PlanningUtils.buildTaskFocusPrompt(plan);
    final String anchorText = "### STRUCTURAL ANCHOR (STRICT FOCUS)\n" + "Active Task: ["
        + PlanningUtils.getTaskIdValue(PlanningUtils.getOpenTask(plan)) + "]\n" + taskPrompt
        + "\n\nFollow the structural thought protocol and your current plan strictly.";
    appended.add(Content.builder().role("user").parts(List.of(Part.fromText(anchorText))).build());

    final LlmRequest updated = request.toBuilder().contents(CollectionUtils.append(request.contents(), appended)).build();
    return Single.just(RequestProcessingResult.create(updated, List.of()));
  }
}
