package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Planning {
    private static final Logger LOG = LoggerFactory.getLogger(Planning.class);
    private static final String PLAN_STATE_KEY = "currentPlan";

    @Schema(name = "create_plan", description = "Create a new plan with subtasks to organize your work. Each subtask can have nested subtasks for hierarchical planning. toolContext is injected by the runtime.")
    public Map<String, Object> createPlan(
            @Schema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
            @Schema(name = "plan_id", description = "Optional ID for the new plan", optional = true) String planId,
            @Schema(name = "name", description = "Name of the plan") String name,
            @Schema(name = "description", description = "What this plan accomplishes") String description,
            @Schema(name = "expected_outcome", description = "Expected result when the plan is complete") String expectedOutcome,
            @Schema(name = "subtasks", description = "List of subtasks, each with 'name', 'description', 'expected_outcome', and optionally nested 'subtasks'") List<Map<String, Object>> subtasksArgs) {

        if (toolContext == null) {
            return Map.of("error", "toolContext is required");
        }

        List<Plan> subtaskPlans = toPlans(subtasksArgs);
        Plan currentPlan = new Plan(name, description, expectedOutcome, subtaskPlans);
        if (StringUtils.isNotBlank(planId)) {
            currentPlan.setId(planId);
        }
        savePlan(toolContext, currentPlan);

        LOG.info("Created plan '{}' with {} subtasks", currentPlan.getId(), subtaskPlans.size());
        return Map.of("status", "success", "plan_id", currentPlan.getId(), "subtask_count", subtaskPlans.size());
    }

    private static List<Plan> toPlans(final List<Map<String, Object>> planArgs) {
        List<Map<String, Object>> planItemsArgs = CollectionUtils.nullSafeMutableList(planArgs);
        List<Plan> planItems = new ArrayList<>();
        for (Map<String, Object> planArgsItem : planItemsArgs) {
            String name = CollectionUtils.getStringValueFromMapSafe(planArgsItem, "name");
            String description = CollectionUtils.getStringValueFromMapSafe(planArgsItem, "description");
            String expectedOutcome = CollectionUtils.getStringValueFromMapSafe(planArgsItem, "expected_outcome");
            Plan planItem = new Plan(name, description, expectedOutcome);
            final String planId = resolvePlanId(planArgsItem);
            if (StringUtils.isNotBlank(planId)) {
                planItem.setId(planId);
            }
            final List<Map<String, Object>> subtaskArgs = CollectionUtils.getListFromMap(planArgsItem, "subtasks");
            if (CollectionUtils.isNotEmpty(subtaskArgs)) {
                planItem.setSubtasks(toPlans(subtaskArgs));
            }
            planItems.add(planItem);
        }
        return planItems;
    }

    private static String resolvePlanId(final Map<String, Object> planArgsItem) {
        return CollectionUtils.getStringValueFromMapSafe(planArgsItem, "plan_id");
    }

    @Schema(name = "update_plan_info", description = "Update the general information of the current plan. toolContext is injected by the runtime.")
    public Map<String, Object> updatePlanInfo(
            @Schema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
            @Schema(name = "plan_id", description = "ID of the plan to update") String planId,
            @Schema(name = "name", description = "New name (optional)") String name,
            @Schema(name = "description", description = "New description (optional)") String description,
            @Schema(name = "expected_outcome", description = "New expected outcome (optional)") String expectedOutcome) {

        Plan currentPlan = getCurrentPlan(toolContext);
        if (currentPlan == null)
            return Map.of("error", "No active plan found");
        if (StringUtils.isBlank(planId))
            return Map.of("error", "plan_id is required");

        Plan targetPlan = findPlanById(currentPlan, planId);
        if (targetPlan == null)
            return Map.of("error", "Plan not found: " + planId);

        if (name != null)
            targetPlan.setName(name);
        if (description != null)
            targetPlan.setDescription(description);
        if (expectedOutcome != null)
            targetPlan.setExpectedOutcome(expectedOutcome);

        savePlan(toolContext, currentPlan);
        return Map.of("status", "success");
    }

    @Schema(name = "revise_current_plan", description = "Replace the current plan's subtasks with a new list. Use this to restructure your plan or add/remove subtasks. toolContext is injected by the runtime.")
    public Map<String, Object> reviseCurrentPlan(
            @Schema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
            @Schema(name = "plan_id", description = "ID of the plan to revise") String planId,
            @Schema(name = "subtasks", description = "Complete new list of subtasks (replaces existing). Can include nested subtasks.") List<Map<String, Object>> subtasksArgs) {

        Plan currentPlan = getCurrentPlan(toolContext);
        if (currentPlan == null)
            return Map.of("error", "No active plan found");
        if (StringUtils.isBlank(planId))
            return Map.of("error", "plan_id is required");

        Plan targetPlan = findPlanById(currentPlan, planId);
        if (targetPlan == null)
            return Map.of("error", "Plan not found: " + planId);

        List<Plan> subtaskPlans = toPlans(subtasksArgs);
        targetPlan.setSubtasks(subtaskPlans);

        savePlan(toolContext, currentPlan);
        return Map.of("status", "success", "subtask_count", subtaskPlans.size());
    }

    @Schema(name = "update_subtask_state", description = "Update the status of a specific subtask. Use this to mark tasks as in progress, done, or abandoned. toolContext is injected by the runtime.")
    public Map<String, Object> updateSubtaskState(
            @Schema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
            @Schema(name = "plan_id", description = "ID of the plan to update") String planId,
            @Schema(name = "status", description = "New status: TODO, IN_PROGRESS, DONE, or ABANDONED") String statusStr,
            @Schema(name = "outcome", description = "Optional outcome description (recommended when marking DONE)", optional = true) String outcome) {

        Plan currentPlan = getCurrentPlan(toolContext);
        if (currentPlan == null)
            return Map.of("error", "No active plan found");
        if (StringUtils.isBlank(planId))
            return Map.of("error", "plan_id is required");

        Plan targetPlan = findPlanById(currentPlan, planId);
        if (targetPlan == null)
            return Map.of("error", "Plan not found: " + planId);

        try {
            PlanStatus status = PlanStatus.valueOf(statusStr.toUpperCase());
            targetPlan.setStatus(status);

            // If marking as DONE and outcome provided, set it
            if (status == PlanStatus.DONE && outcome != null && !outcome.isBlank()) {
                targetPlan.setOutcome(outcome);
            }

            savePlan(toolContext, currentPlan);
            return Map.of("status", "success");
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid status: " + statusStr);
        }
    }

    @Schema(name = "finish_plan", description = "Mark the entire plan as complete or abandoned with a final outcome. toolContext is injected by the runtime.")
    public Map<String, Object> finishPlan(
            @Schema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
            @Schema(name = "plan_id", description = "ID of the plan to finish") String planId,
            @Schema(name = "status", description = "Final status: DONE or ABANDONED") String statusStr,
            @Schema(name = "outcome", description = "Summary of what was accomplished or why it was abandoned") String outcome) {

        Plan currentPlan = getCurrentPlan(toolContext);
        if (currentPlan == null)
            return Map.of("error", "No active plan found");
        if (StringUtils.isBlank(planId))
            return Map.of("error", "plan_id is required");

        Plan targetPlan = findPlanById(currentPlan, planId);
        if (targetPlan == null)
            return Map.of("error", "Plan not found: " + planId);

        try {
            targetPlan.finish(PlanStatus.valueOf(statusStr.toUpperCase()), outcome);
            savePlan(toolContext, currentPlan);
            return Map.of("status", "success");
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid status: " + statusStr);
        }
    }

    @Schema(name = "view_current_plan", description = "View the complete plan including all subtasks, their statuses, and progress. toolContext is injected by the runtime.")
    public Plan viewCurrentPlan(@Schema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext) {
        return getCurrentPlan(toolContext);
    }

    private static Plan getCurrentPlan(final ToolContext toolContext) {
        if (toolContext == null) {
            LOG.warn("toolContext is missing for plan access");
            return null;
        }
        return CollectionUtils.getValueFromMap(toolContext.state(), PLAN_STATE_KEY, Plan.class);
    }

    private static void savePlan(final ToolContext toolContext, final Plan plan) {
        if (toolContext == null) {
            LOG.warn("toolContext is missing for plan save");
            return;
        }
        toolContext.state().put(PLAN_STATE_KEY, plan);
    }

    private static Plan findPlanById(final Plan plan, final String planId) {
        if (plan == null || StringUtils.isBlank(planId)) {
            return null;
        }
        if (planId.equals(plan.getId())) {
            return plan;
        }
        return findPlanById(plan.getSubtasks(), planId);
    }

    private static Plan findPlanById(final List<Plan> planItems, final String planId) {
        if (planItems == null || StringUtils.isBlank(planId)) {
            return null;
        }
        for (Plan planItem : planItems) {
            Plan match = findPlanById(planItem, planId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }
}
