package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CreatePlanTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(CreatePlanTool.class);
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_PLAN,
          """
          Initialises a new plan with a title, goal, and initial list of tasks. Only one active \
          (non-finished) plan may exist at a time. \
          Returns: { status: "success", createdPlan } on success, or { error } on failure.""",
          Map.of());

  public CreatePlanTool() {
    super(DESCRIPTOR);
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

    final String validationError = currentPlan.validate();
    if (StringUtils.isNotBlank(validationError)) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    sessionState.updatePlan(currentPlan);

    LOG.debug("Created plan '{}' with {} tasks", currentPlan.getPlanId(), tasks.size());
    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.SUCCESS,
            "message",
            "Plan '" + title + "' created with " + tasks.size() + " tasks.",
            "createdPlan",
            currentPlan));
  }
}
