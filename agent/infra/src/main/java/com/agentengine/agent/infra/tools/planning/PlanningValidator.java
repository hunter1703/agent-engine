package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanningValidator {

    private PlanningValidator() {}

    public static String validatePlan(final Plan plan) {
        if (plan == null) {
            return "Plan is required.";
        }
        final String planLabel = describePlan(plan);
        if (plan.getStatus() == null || plan.getStatus() == PlanStatus.UNKNOWN) {
            return planLabel + " has unknown status.";
        }
        final Map<String, Task> tasksById = new LinkedHashMap<>();
        for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
            final String taskId = PlanningUtils.getTaskIdValue(task);
            if (StringUtils.isBlank(taskId)) {
                continue;
            }
            final Task existing = tasksById.putIfAbsent(taskId, task);
            if (existing != null) {
                return "Duplicate task id "
                        + taskId
                        + " found in "
                        + planLabel
                        + " for "
                        + describeTask(existing)
                        + " and "
                        + describeTask(task)
                        + ".";
            }
        }
        final Map<String, List<Task>> parentVsTasks = PlanningUtils.groupTasksByParent(plan);
        final Task openTask = findFirstOpenTask(plan);
        if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS && openTask != null) {
            return planLabel
                    + " is "
                    + describePlanStatus(plan)
                    + " but "
                    + describeTask(openTask)
                    + " is "
                    + describeTaskStatus(openTask)
                    + ".";
        }
        if (!PlanningUtils.isTerminalStatus(plan.getStatus()) && StringUtils.isNotBlank(plan.getResult())) {
            return planLabel + " is " + describePlanStatus(plan) + " but has a result.";
        }
        if (PlanningUtils.isTerminalStatus(plan.getStatus()) && StringUtils.isBlank(plan.getResult())) {
            return planLabel + " is " + describePlanStatus(plan) + " but is missing a result.";
        }
        for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
            final String parentId = task.getParentId();
            if (StringUtils.isNotBlank(parentId) && !tasksById.containsKey(parentId)) {
                return describeTask(task) + " references missing parent " + parentId + ".";
            }
            final TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
            if (status == TaskStatus.UNKNOWN) {
                return describeTask(task) + " has unknown status.";
            }
            if (!PlanningUtils.isTerminalStatus(status) && StringUtils.isNotBlank(task.getResult())) {
                return describeTask(task) + " is " + describeTaskStatus(task) + " but has a result.";
            }
            if (status == TaskStatus.IN_PROGRESS) {
                final String ancestorError = validateAncestorsInProgress(task, tasksById);
                if (ancestorError != null) {
                    return ancestorError;
                }
            }
            if (PlanningUtils.isTerminalStatus(status)) {
                final Task blocking = findNonTerminalDescendant(task, parentVsTasks);
                if (blocking != null) {
                    return describeTask(task)
                            + " is "
                            + status.getValue()
                            + " but child tasks are not terminal (e.g., "
                            + describeTask(blocking)
                            + " is "
                            + describeTaskStatus(blocking)
                            + ").";
                }
                if (StringUtils.isBlank(task.getResult())) {
                    return describeTask(task) + " is " + status.getValue() + " but is missing a result.";
                }
            }
        }
        final List<Task> inProgressTasks = collectInProgressTasks(plan, null, null, false);
        final String lineageError = validateSingleLineage(inProgressTasks, tasksById);
        if (lineageError != null) {
            return lineageError;
        }
        return validateInProgressOrder(plan, tasksById, parentVsTasks, inProgressTasks);
    }

    public static String canAddTask(final Plan plan, final Task task) {
        if (plan == null) {
            return "Plan is required.";
        }
        if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
            return describePlan(plan)
                    + " is "
                    + describePlanStatus(plan)
                    + "; must be "
                    + PlanStatus.IN_PROGRESS.getValue()
                    + " to add tasks.";
        }
        if (task == null) {
            return "Task is required for " + describePlan(plan) + ".";
        }
        if (PlanningUtils.getTaskStatusEnum(task) == TaskStatus.UNKNOWN) {
            return describeTask(task) + " has unknown status.";
        }
        final Map<String, Task> tasksById = PlanningUtils.getTasksById(plan);
        final String taskId = PlanningUtils.getTaskIdValue(task);
        if (StringUtils.isNotBlank(taskId) && tasksById.containsKey(taskId)) {
            return "Task id "
                    + taskId
                    + " already exists in "
                    + describePlan(plan)
                    + " (existing "
                    + describeTask(tasksById.get(taskId))
                    + ").";
        }
        final String parentId = task.getParentId();
        if (StringUtils.isNotBlank(parentId)) {
            final Task parent = tasksById.get(parentId);
            if (parent == null) {
                return describeTask(task) + " references missing parent " + parentId + ".";
            }
            if (PlanningUtils.isTerminalStatus(PlanningUtils.getTaskStatusEnum(parent))) {
                return "Parent "
                        + describeTask(parent)
                        + " is "
                        + describeTaskStatus(parent)
                        + "; cannot add child "
                        + describeTask(task)
                        + ".";
            }
        }
        final TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
        if (status == TaskStatus.IN_PROGRESS) {
            if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
                return describePlan(plan)
                        + " is "
                        + describePlanStatus(plan)
                        + "; must be "
                        + PlanStatus.IN_PROGRESS.getValue()
                        + " before starting tasks.";
            }
            final String ancestorError = validateAncestorsInProgress(task, tasksById);
            if (ancestorError != null) {
                return ancestorError;
            }
            final List<Task> inProgressTasks = collectInProgressTasks(plan, task, status, true);
            final String lineageError = validateSingleLineage(inProgressTasks, tasksById);
            if (lineageError != null) {
                return lineageError;
            }
            return validateInProgressOrder(plan, tasksById, PlanningUtils.groupTasksByParent(plan), inProgressTasks);
        }
        return null;
    }

    public static String canUpdateTask(
            final Plan plan, final Task task, final TaskStatus newStatus, final String result) {
        if (plan == null || task == null || newStatus == null) {
            return null;
        }
        if (newStatus == TaskStatus.UNKNOWN) {
            return describeTask(task) + " has unknown status.";
        }
        if (PlanningUtils.isTerminalStatus(newStatus) && StringUtils.isBlank(result)) {
            return "The 'result' field is required when marking a task as " + newStatus.getValue() + ".";
        }
        if (!PlanningUtils.isTerminalStatus(newStatus) && StringUtils.isNotBlank(result)) {
            return "The 'result' field can only be set when marking a task as "
                    + PlanningUtils.getTerminalStatuses(TaskStatus.class)
                    + ".";
        }
        if (PlanningUtils.isTerminalStatus(plan.getStatus())) {
            return describePlan(plan)
                    + " is "
                    + describePlanStatus(plan)
                    + "; must be "
                    + PlanStatus.IN_PROGRESS.getValue()
                    + " to update tasks.";
        }
        final TaskStatus currentStatus = PlanningUtils.getTaskStatusEnum(task);
        if (currentStatus == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.TODO) {
            return describeTask(task)
                    + " cannot move from "
                    + TaskStatus.IN_PROGRESS.getValue()
                    + " back to "
                    + TaskStatus.TODO.getValue()
                    + ".";
        }
        if (PlanningUtils.isTerminalStatus(currentStatus) && !PlanningUtils.isTerminalStatus(newStatus)) {
            return describeTask(task) + " is " + currentStatus.getValue() + "; cannot reopen it.";
        }

        final Map<String, Task> tasksById = PlanningUtils.getTasksById(plan);
        final Map<String, List<Task>> taskTree = PlanningUtils.groupTasksByParent(plan);

        if (newStatus == TaskStatus.IN_PROGRESS) {
            if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
                return describePlan(plan)
                        + " is "
                        + describePlanStatus(plan)
                        + "; must be "
                        + PlanStatus.IN_PROGRESS.getValue()
                        + " before starting tasks.";
            }
            final String ancestorError = validateAncestorsInProgress(task, tasksById);
            if (ancestorError != null) {
                return ancestorError;
            }
            if (currentStatus != TaskStatus.IN_PROGRESS) {
                final String orderError = validateNextTaskToStart(plan, task, taskTree);
                if (orderError != null) {
                    return orderError;
                }
            }
        }

        if (newStatus == TaskStatus.TODO) {
            final Task descendant = findInProgressDescendant(task, taskTree);
            if (descendant != null) {
                return describeTask(task)
                        + " cannot move to todo while child "
                        + describeTask(descendant)
                        + " is "
                        + TaskStatus.IN_PROGRESS.getValue()
                        + ".";
            }
        }

        if (PlanningUtils.isTerminalStatus(newStatus)) {
            final Task blocking = findNonTerminalDescendant(task, taskTree);
            if (blocking != null) {
                return describeTask(task)
                        + " cannot be marked "
                        + newStatus.getValue()
                        + " while child tasks are not terminal (e.g., "
                        + describeTask(blocking)
                        + " is "
                        + describeTaskStatus(blocking)
                        + ").";
            }
            if (StringUtils.isBlank(result)) {
                return describeTask(task) + " is being marked " + newStatus.getValue() + " but is missing a result.";
            }
        }

        final List<Task> inProgressTasks = collectInProgressTasks(plan, task, newStatus, false);
        final String lineageError = validateSingleLineage(inProgressTasks, tasksById);
        if (lineageError != null) {
            return lineageError;
        }
        return validateInProgressOrder(plan, tasksById, taskTree, inProgressTasks);
    }

    @Deprecated
    public static String canUpdateTask(final Plan plan, final Task task, final TaskStatus newStatus) {
        return canUpdateTask(plan, task, newStatus, null);
    }

    public static String canFinishPlan(final Plan plan, final PlanStatus newStatus, final String result) {
        if (plan == null || newStatus == null) {
            return null;
        }
        if (newStatus == PlanStatus.UNKNOWN) {
            return describePlan(plan) + " has unknown status.";
        }
        if (!PlanningUtils.isTerminalStatus(newStatus)) {
            return "The '"
                    + newStatus
                    + "' is not a terminal status. Allowed values are ["
                    + PlanningUtils.getTerminalStatuses(PlanStatus.class)
                    + "].";
        }
        final Task openTask = findFirstOpenTask(plan);
        if (openTask != null) {
            return describePlan(plan)
                    + " cannot be marked "
                    + newStatus.getValue()
                    + " while "
                    + describeTask(openTask)
                    + " is "
                    + describeTaskStatus(openTask)
                    + ".";
        }
        if (StringUtils.isBlank(result)) {
            return "The 'result' field is required when marking a plan as " + newStatus.getValue() + ".";
        }
        return null;
    }

    public static String getPrematureCompleteViolation(final Plan plan) {
        if (plan == null) {
            return null;
        }
        final PlanStatus status = plan.getStatus();
        if (!PlanningUtils.isTerminalStatus(status)) {
            final Task openTask = findFirstOpenTask(plan);
            final String openTaskMessage =
                    openTask == null ? "" : " and has open tasks (e.g., " + describeTask(openTask) + ")";
            return "Cannot submit final answer while "
                    + describePlan(plan)
                    + " is "
                    + describePlanStatus(plan)
                    + openTaskMessage
                    + ". Please complete your plan first.";
        }
        return null;
    }

    private static Task findFirstOpenTask(final Plan plan) {
        for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
            if (isOpenTask(task)) {
                return task;
            }
        }
        return null;
    }

    private static boolean isOpenTask(final Task task) {
        final TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
        return status == TaskStatus.TODO || status == TaskStatus.IN_PROGRESS || status == TaskStatus.UNKNOWN;
    }

    private static String validateAncestorsInProgress(final Task task, final Map<String, Task> tasksById) {
        if (task == null) {
            return null;
        }
        String parentId = task.getParentId();
        while (StringUtils.isNotBlank(parentId)) {
            final Task parent = tasksById.get(parentId);
            if (parent == null) {
                return describeTask(task) + " references missing parent " + parentId + ".";
            }
            if (PlanningUtils.getTaskStatusEnum(parent) != TaskStatus.IN_PROGRESS) {
                return "Parent "
                        + describeTask(parent)
                        + " must be "
                        + TaskStatus.IN_PROGRESS.getValue()
                        + " before starting child "
                        + describeTask(task)
                        + ".";
            }
            parentId = parent.getParentId();
        }
        return null;
    }

    private static Task findInProgressDescendant(final Task task, final Map<String, List<Task>> taskTree) {
        final String taskId = PlanningUtils.getTaskIdValue(task);
        for (Task child : CollectionUtils.nullSafeList(taskTree.get(taskId))) {
            if (PlanningUtils.getTaskStatusEnum(child) == TaskStatus.IN_PROGRESS) {
                return child;
            }
            final Task nested = findInProgressDescendant(child, taskTree);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Task findNonTerminalDescendant(final Task task, final Map<String, List<Task>> taskTree) {
        final String taskId = PlanningUtils.getTaskIdValue(task);
        for (Task child : CollectionUtils.nullSafeList(taskTree.get(taskId))) {
            if (!PlanningUtils.isTerminalStatus(PlanningUtils.getTaskStatusEnum(child))) {
                return child;
            }
            final Task nested = findNonTerminalDescendant(child, taskTree);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String validateNextTaskToStart(
            final Plan plan, final Task task, final Map<String, List<Task>> taskTree) {
        final Task nextTask = PlanningUtils.findNextTodoTask(plan);
        if (nextTask == null) {
            return "No pending tasks remain to start in " + describePlan(plan) + ".";
        }
        if (nextTask == task) {
            return null;
        }
        final String taskId = PlanningUtils.getTaskIdValue(task);
        final String nextId = PlanningUtils.getTaskIdValue(nextTask);
        if (StringUtils.isNotBlank(taskId) && taskId.equals(nextId)) {
            return null;
        }
        return "Next task to start is " + describeTask(nextTask) + "; cannot start " + describeTask(task) + ".";
    }

    private static String validateInProgressOrder(
            final Plan plan,
            final Map<String, Task> tasksById,
            final Map<String, List<Task>> taskTree,
            final List<Task> inProgressTasks) {
        if (CollectionUtils.isEmpty(inProgressTasks)) {
            return null;
        }
        final Task nextTodo = PlanningUtils.findNextTodoTask(plan);
        if (nextTodo == null) {
            return null;
        }
        final Set<String> lineage = collectLineageIds(nextTodo, tasksById);
        for (Task task : inProgressTasks) {
            final String taskId = PlanningUtils.getTaskIdValue(task);
            if (StringUtils.isBlank(taskId)) {
                return "In-progress " + describeTask(task) + " is missing an id.";
            }
            if (!lineage.contains(taskId)) {
                return "In-progress task "
                        + describeTask(task)
                        + " is out of order; next task to start is "
                        + describeTask(nextTodo)
                        + " in "
                        + describePlan(plan)
                        + ".";
            }
        }
        return null;
    }

    private static List<Task> collectInProgressTasks(
            final Plan plan, final Task updatedTask, final TaskStatus newStatus, final boolean includeMissingTask) {
        final List<Task> inProgress = new ArrayList<>();
        final String updatedId = PlanningUtils.getTaskIdValue(updatedTask);
        boolean updatedFound = false;
        for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
            TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
            if (task == updatedTask
                    || (StringUtils.isNotBlank(updatedId) && updatedId.equals(PlanningUtils.getTaskIdValue(task)))) {
                status = newStatus;
                updatedFound = true;
            }
            if (status == TaskStatus.IN_PROGRESS) {
                inProgress.add(task);
            }
        }
        if (includeMissingTask && !updatedFound && newStatus == TaskStatus.IN_PROGRESS) {
            inProgress.add(updatedTask);
        }
        return inProgress;
    }

    private static String validateSingleLineage(final List<Task> inProgressTasks, final Map<String, Task> tasksById) {
        if (inProgressTasks.size() <= 1) {
            return null;
        }
        final Task deepest = findDeepestTask(inProgressTasks, tasksById);
        if (deepest == null) {
            return null;
        }
        final Set<String> lineageIds = collectLineageIds(deepest, tasksById);
        for (Task task : inProgressTasks) {
            final String taskId = PlanningUtils.getTaskIdValue(task);
            if (StringUtils.isBlank(taskId)) {
                return "In-progress " + describeTask(task) + " is missing an id.";
            }
            if (!lineageIds.contains(taskId)) {
                return "Only one in-progress task lineage is allowed; "
                        + describeTask(task)
                        + " is outside the lineage rooted at "
                        + describeTask(deepest)
                        + ".";
            }
        }
        return null;
    }

    private static Task findDeepestTask(final List<Task> tasks, final Map<String, Task> tasksById) {
        Task deepest = null;
        int maxDepth = -1;
        for (Task task : tasks) {
            final int depth = taskDepth(task, tasksById);
            if (depth > maxDepth) {
                maxDepth = depth;
                deepest = task;
            }
        }
        return deepest;
    }

    private static int taskDepth(final Task task, final Map<String, Task> tasksById) {
        int depth = 0;
        String parentId = task == null ? null : task.getParentId();
        final Set<String> visited = new HashSet<>();
        while (StringUtils.isNotBlank(parentId) && visited.add(parentId)) {
            final Task parent = tasksById.get(parentId);
            if (parent == null) {
                break;
            }
            depth++;
            parentId = parent.getParentId();
        }
        return depth;
    }

    private static Set<String> collectLineageIds(final Task task, final Map<String, Task> tasksById) {
        final Set<String> lineage = new HashSet<>();
        Task current = task;
        while (current != null) {
            final String taskId = PlanningUtils.getTaskIdValue(current);
            if (StringUtils.isBlank(taskId) || !lineage.add(taskId)) {
                break;
            }
            final String parentId = current.getParentId();
            if (StringUtils.isBlank(parentId)) {
                break;
            }
            current = tasksById.get(parentId);
        }
        return lineage;
    }

    private static String describePlan(final Plan plan) {
        if (plan == null) {
            return "plan <unknown>";
        }
        final String id = plan.getPlanId();
        final String title = plan.getTitle();
        if (StringUtils.isNotBlank(id) && StringUtils.isNotBlank(title)) {
            return "plan " + id + " (" + title + ")";
        }
        if (StringUtils.isNotBlank(id)) {
            return "plan " + id;
        }
        if (StringUtils.isNotBlank(title)) {
            return "plan " + title;
        }
        return "plan <unknown>";
    }

    private static String describePlanStatus(final Plan plan) {
        if (plan == null || plan.getStatus() == null) {
            return PlanStatus.UNKNOWN.getValue();
        }
        return plan.getStatus().getValue();
    }

    private static String describeTask(final Task task) {
        if (task == null) {
            return "task <unknown>";
        }
        final String id = PlanningUtils.getTaskIdValue(task);
        final String name = task.getName();
        if (StringUtils.isNotBlank(id) && StringUtils.isNotBlank(name)) {
            return "task " + id + " (" + name + ")";
        }
        if (StringUtils.isNotBlank(id)) {
            return "task " + id;
        }
        if (StringUtils.isNotBlank(name)) {
            return "task " + name;
        }
        return "task <unknown>";
    }

    private static String describeTaskStatus(final Task task) {
        return PlanningUtils.getTaskStatusEnum(task).getValue();
    }
}
