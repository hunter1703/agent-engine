package com.agentengine.agent.infra.agents.processors.request;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.Constants;
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
 * Injects plan context into requests to maintain task focus.
 *
 * <p>When a plan is active, this processor adds two pieces of context to the request: (1) a
 * high-level summary of the overall plan, and (2) a structural anchor highlighting the currently
 * open task. This directs the model's attention to the immediate goal and prevents drift from the
 * plan.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li>If no plan is active or the plan is already completed, the request passes through
 *       unchanged.
 *   <li>If a plan is active, two content blocks are appended to the request:
 *       <ul>
 *         <li>A plan summary highlighting all tasks and current progress.
 *         <li>A task focus anchor identifying the open task and its requirements.
 *       </ul>
 *   <li>The appended content is added as user messages (role="user").
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 *   <li>Session plan state must be initialized in {@code RunUtils.getOrInitState(context)}.
 *   <li>The incoming request contains valid content that can be extended.
 * </ul>
 */
public final class PlanningRequestProcessor implements RequestProcessor {
    public static final PlanningRequestProcessor INSTANCE = new PlanningRequestProcessor();

    private PlanningRequestProcessor() {}

    @Override
    public Single<RequestProcessingResult> processRequest(final InvocationContext context, final LlmRequest request) {
        final RunState runState = RunUtils.getOrInitState(context);
        if (!runState.hasActivePlan()) {
            return Single.just(RequestProcessingResult.create(request, List.of()));
        }

        final Plan plan = runState.plan();
        final List<Content> appended = new ArrayList<>();
        // 1. High-level plan summary
        final String summary = PlanningUtils.buildPlanSummary(plan);
        appended.add(Content.builder()
                .role(Constants.AUTHOR_USER)
                .parts(List.of(Part.fromText(summary)))
                .build());

        // 2. Low-level task focus (structural anchor).
        final String taskPrompt = PlanningUtils.buildTaskFocusPrompt(plan);
        final String anchorText = "### STRUCTURAL ANCHOR (STRICT FOCUS)\n"
                + "Active Task: ["
                + PlanningUtils.getTaskIdValue(PlanningUtils.getOpenTask(plan))
                + "]\n"
                + taskPrompt
                + "\n\nFollow the structural thought protocol and your current plan strictly.";
        appended.add(Content.builder()
                .role(Constants.AUTHOR_USER)
                .parts(List.of(Part.fromText(anchorText)))
                .build());

        final LlmRequest updated = request.toBuilder()
                .contents(CollectionUtils.append(request.contents(), appended))
                .build();
        return Single.just(RequestProcessingResult.create(updated, List.of()));
    }
}
