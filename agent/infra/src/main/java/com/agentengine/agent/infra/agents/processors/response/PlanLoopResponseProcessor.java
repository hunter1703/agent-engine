package com.agentengine.agent.infra.agents.processors.response;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.agent.infra.utils.Reminder;
import com.agentengine.agent.infra.utils.ResponseUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Enforces plan/task completion before final-answer responses.
 *
 * <p>Validates that any final-answer response corresponds to a completed plan. When a final answer
 * is signaled while tasks remain open, the response is rejected and continuation is requested so
 * the model can continue working toward completion.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li>Final-answer responses are allowed only when the plan is absent or already completed.
 *   <li>If a final answer is signaled while the plan has open tasks, a violation is emitted and
 *       continuation is requested (the model regenerates without finalizing).
 *   <li>Partial responses and tool-call responses pass through unchanged.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 *   <li>Session plan state must be initialized in {@code RunUtils.getOrInitState(context)}.
 *   <li>Prior processors have completed their modifications to the response.
 * </ul>
 */
public final class PlanLoopResponseProcessor implements ResponseProcessor {
    public static final PlanLoopResponseProcessor INSTANCE = new PlanLoopResponseProcessor();

    private PlanLoopResponseProcessor() {}

    @Override
    public Single<ResponseProcessingResult> processResponse(
            final InvocationContext context, final LlmResponse response) {
        if (response.partial().orElse(false)) {
            return ResponseUtils.single(response);
        }

        final RunState runState = RunUtils.getOrInitState(context);

        if (!runState.hasActivePlan()) {
            // Plan completed or absent — clear any stale plan reminder
            runState.removeReminder("plan");
            return ResponseUtils.single(response);
        }

        final Plan plan = runState.plan();

        // Sync the plan reminder to reflect current state
        runState.addReminder(new Reminder(Reminder.GROUP_ACTIVE_PLAN, "plan", buildPlanBrief(plan)));

        if (!ResponseUtils.isFinalAnswer(response)) {
            return ResponseUtils.single(response);
        }

        final String planViolation = PlanningValidator.getPrematureCompleteViolation(plan);
        if (StringUtils.isBlank(planViolation)) {
            return ResponseUtils.single(response);
        }
        runState.requestContinuation(Violation.builder("final_answer_validation")
                .message(planViolation)
                .build());
        return ResponseUtils.single(response);
    }

    private static String buildPlanBrief(final Plan plan) {
        final StringBuilder sb = new StringBuilder();
        sb.append(PlanningUtils.buildPlanSummary(plan));

        final Task openTask = PlanningUtils.getOpenTask(plan);
        if (openTask != null) {
            sb.append("\n\nActive task — stay focused on this:\n");
            sb.append(PlanningUtils.buildTaskFocusPrompt(plan));
            sb.append("\n→ Do not start a new task until this one is complete or explicitly abandoned.");
        } else {
            final Task nextTask = PlanningUtils.findNextTodoTask(plan);
            if (nextTask != null) {
                sb.append("\n\nNo active task — pick up the next one:\n");
                sb.append("Task [")
                        .append(PlanningUtils.getTaskIdValue(nextTask))
                        .append("] — ")
                        .append(nextTask.getName());
                if (StringUtils.isNotBlank(nextTask.getGoal())) {
                    sb.append("\nGoal: ").append(nextTask.getGoal());
                }
                sb.append("\n→ Mark it in_progress before starting work.");
            }
        }
        return sb.toString().trim();
    }
}
