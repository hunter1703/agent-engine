package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
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
    final String taskPrompt = PlanningUtils.buildTaskFocusPrompt(plan);
    if (StringUtils.isBlank(taskPrompt)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    final LlmRequest updated = request.toBuilder().appendInstructions(List.of(taskPrompt)).build();
    return Single.just(RequestProcessingResult.create(updated, List.of()));
  }
}
