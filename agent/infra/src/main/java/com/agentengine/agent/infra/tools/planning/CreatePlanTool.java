package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CreatePlanTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(CreatePlanTool.class);
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_PLAN,
"""
           Initialises a new plan with a title, goal, and initial list of tasks. Only one active \
           (non-finished) plan may exist at a time. The first actionable task (and its parent lineage \
           if nested) is automatically started and marked in progress. When finished with each task, \
           call %s with a concise outcome result. Completing a task automatically starts the next \
           actionable task and marks it in progress. All tasks must be completed before delivering a \
           final user answer. Work on one task at a time — do not start working on other tasks before \
           completing the active one. \
           Returns: { status: "success", createdPlan, active_task, active_lineage, message } on success, or { error } on failure."""
              .formatted(Constants.ToolNames.COMPLETE_TASK),
          Map.of());

  private final ValidationService validationService;

  public CreatePlanTool(final ValidationService validationService) {
    super(DESCRIPTOR);
    this.validationService = validationService;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolArg(name = "title", description = "Short human-readable label that identifies the plan.")
          String title,
      @ToolArg(
          name = "goal",
          description =
              """
              Clear description of the objective this plan is intended to achieve. Guides task \
              prioritisation and completion criteria.""")
          String goal,
      @ToolArg(name = "tasks", description = "Initial list of tasks comprising the plan.")
          List<Task> tasks) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final Plan existingPlan = sessionState.plan();
    if (existingPlan != null) {
      final PlanStatus status = existingPlan.getStatus();
      if (!status.isTerminal()) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "Active plan '"
                    + existingPlan.getTitle()
                    + "' already exists — see the Active Plan reminder above for its current "
                    + "state. Finish the active plan before creating a new one."));
      }
    }

    if (tasks != null) {
      final Map<String, String> nameLookup = new HashMap<>();
      for (final Task task : tasks) {
        if (StringUtils.isNotBlank(task.getName())) {
          nameLookup.put(task.getName().trim().toLowerCase(), task.getName());
        }
        task.setStatus(TaskStatus.TODO);
        task.setResult(null);
      }

      for (final Task task : tasks) {
        final String parent = task.getParent();
        if (StringUtils.isNotBlank(parent)) {
          final String resolved = nameLookup.get(parent.trim().toLowerCase());
          if (resolved != null) {
            task.setParent(resolved);
          }
        }
      }
    }

    final Plan currentPlan = new Plan(title, goal, tasks);
    final List<Task> activeLineage =
        PlanningUtils.activateNextActionableLineage(currentPlan);

    final List<String> validationErrors = validationService.errors(currentPlan);
    if (!validationErrors.isEmpty()) {
      return ToolOutput.direct(Map.of("error", String.join("; ", validationErrors)));
    }

    sessionState.updatePlan(currentPlan);

    LOG.debug(
        "Created plan '{}' with {} tasks",
        currentPlan.getTitle(),
        tasks != null ? tasks.size() : 0);
    final Map<String, Object> output = new LinkedHashMap<>();
    output.put(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS);
    output.put("createdPlan", currentPlan);
    if (!activeLineage.isEmpty()) {
      final Task activeTask = activeLineage.getLast();
      final List<String> activeLineageNames = PlanningUtils.taskNames(activeLineage);
      output.put("active_task", activeTask.getName());
      output.put("active_lineage", activeLineageNames);
      final String lineageStr = String.join(" > ", activeLineageNames);
      output.put(
          "message",
          "Plan '"
              + title
              + "' created. Active task is '"
              + activeTask.getName()
              + "' (active branch: "
              + lineageStr
              + "). Work on this task and call complete_task when finished.");
    } else {
      output.put("message", "Plan '" + title + "' created.");
    }
    return ToolOutput.direct(output);
  }
}
