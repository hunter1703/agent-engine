package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

public final class PlanContextRequestProcessor implements RequestProcessor {
  private static final String PLAN_STATE_KEY = PlanningUtils.PLAN_STATE_KEY;

  @Override
  public Single<RequestProcessingResult> processRequest(final InvocationContext context, final LlmRequest request) {
    final Plan plan = extractPlan(context);
    if (plan == null) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    final String summary = PlanningUtils.buildPlanSummary(plan);
    if (StringUtils.isBlank(summary)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    final LlmRequest updatedRequest = request.toBuilder().appendInstructions(List.of(summary)).build();
    return Single.just(RequestProcessingResult.create(updatedRequest, List.of()));
  }

  private static Plan extractPlan(final InvocationContext context) {
    if (context == null) {
      return null;
    }
    final Session session = context.session();
    if (session == null || session.state() == null) {
      return null;
    }
    return CollectionUtils.getValueFromMap(session.state(), PLAN_STATE_KEY, Plan.class);
  }

}
