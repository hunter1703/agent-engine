package com.agentengine.engine.agents.flows;

import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

public final class PlanContextRequestProcessor implements RequestProcessor {
  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final Plan plan = PlanningUtils.getPlanFromContext(context);
    if (plan == null) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    final String summary = PlanningUtils.buildPlanSummary(plan);
    if (StringUtils.isBlank(summary)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    final LlmRequest updatedRequest =
        request.toBuilder().appendInstructions(List.of(summary)).build();
    return Single.just(RequestProcessingResult.create(updatedRequest, List.of()));
  }
}
