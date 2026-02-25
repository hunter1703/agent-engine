package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

public class UpdateTaskStatusTool extends Tool {

    public UpdateTaskStatusTool(ToolDescriptor toolDescriptor) {
        super(toolDescriptor);
    }

    protected Map<String, Object> _execute(ToolContext toolContext, String taskId, String name, String goal, String description, TaskStatus newStatus, String result) {
        final Plan currentPlan = PlanningUtils.getCurrentPlan(toolContext);
        if (currentPlan == null) {
            return Map.of("error", "No active plan found");
        }

        final Task task = PlanningUtils.findTaskById(currentPlan, taskId);
        if (task == null) {
            return Map.of("error", "Task not found with ID: " + taskId);
        }

        newStatus = newStatus != null ? newStatus : task.getStatus();
        result = StringUtils.isNotBlank(result) ? result : task.getResult();
        final String validationError = currentPlan.canUpdateTask(task, newStatus, result);
        if (validationError != null) {
            return Map.of("error", validationError);
        }

        if (StringUtils.isNotBlank(name)) {
            task.setName(name);
        }
        if (StringUtils.isNotBlank(goal)) {
            task.setGoal(goal);
        }
        if (StringUtils.isNotBlank(description)) {
            task.setDescription(description);
        }
        task.setStatus(newStatus);
        task.setResult(result);
        PlanningUtils.savePlan(toolContext, currentPlan);
        return Map.of("status", "success", "task_id", taskId, "new_status", newStatus.getValue());
    }
}
