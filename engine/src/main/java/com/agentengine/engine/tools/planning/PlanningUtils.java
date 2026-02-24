package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.Session;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlanningUtils {
  private static final int MAX_SECTION_ITEMS = 5;
  private static final int MAX_FOCUS_TASKS = 1;
  private static final Logger LOG = LoggerFactory.getLogger(PlanningUtils.class);
  public static final String PLAN_STATE_KEY = "currentPlan";

  private PlanningUtils() {}

  /**
   * Builds a tree-like string representation of the root plan and its current task hierarchy.
   * This summary is injected into the LLM context to help the agent track progress.
   *
   * Example prompt output:
   *
   * PLAN CONTEXT
   * Title: Refactor API layer
   * Status: in_progress
   * Goal: Migrate all endpoints to flat Task models to resolve recursion issues.
   * Task IDs: Use the IDs below when updating tasks.
   *
   * Progress: 1/5 completed (1 in progress, 3 pending)
   *
   * CURRENT FOCUS:
   * - Refactor API layer > Update Plan bean (id: task-2, parent: none) [in_progress] | goal: Update Plan model
   *
   * NEXT ITEMS:
   * - Refactor API layer > Define Task bean (id: task-1, parent: none) [todo] | goal: Create Task model
   * - Refactor API layer > Remove subtasks field (id: task-3, parent: task-2) [todo] | goal: Drop recursion
   * - Refactor API layer > Add tasks field (id: task-4, parent: task-2) [todo] | goal: Flat list
   *
   * RECENT COMPLETED:
   * - Refactor API layer > Verify llama-server fix (id: task-5, parent: none) [done] | result: Smoke tested
   *
   * Plan Tree (compact):
   * - [todo] Define Task bean (id: task-1, parent: none)
   * - [in_progress] Update Plan bean (id: task-2, parent: none)
   *   - [todo] Remove subtasks field (id: task-3, parent: task-2)
   *   - [todo] Add tasks field (id: task-4, parent: task-2)
   * - [done] Verify llama-server recursion fix (id: task-5, parent: none)
   *
   * @param plan the root Plan to summarize
   * @return a formatted hierarchical string
   */
  public static String buildPlanSummary(final Plan plan) {
    if (plan == null) {
      return "No active plan found.";
    }

    final String title = planTitle(plan);

    final StringBuilder builder = new StringBuilder();
    builder.append("PLAN CONTEXT");
    builder.append("\nTitle: ").append(title);
    builder.append("\nStatus: ").append(getPlanStatus(plan));
    if (StringUtils.isNotBlank(plan.getGoal())) {
      builder.append("\nGoal: ").append(plan.getGoal());
    }
    if (StringUtils.isNotBlank(plan.getResult())) {
      builder.append("\nResult: ").append(plan.getResult());
    }
    builder.append("\nTask IDs: Use the IDs below when updating tasks.");
    builder.append("\n\n");

    final Map<String, Task> tasksById = getTasksById(plan);
    final Map<String, List<Task>> taskVsAncestors = buildTaskAncestorMap(plan, tasksById);
    final Map<String, List<Task>> taskVsChildren = groupTasksByParent(plan);
    final List<Task> orderedTasks = collectOrderedTasks(taskVsChildren);
    appendProgressSummary(builder, orderedTasks);

    appendPlanSection(
        builder,
        "CURRENT FOCUS",
        filterByStatus(orderedTasks, TaskStatus.IN_PROGRESS),
        MAX_SECTION_ITEMS,
        true,
        false,
        title,
        tasksById,
        taskVsAncestors);
    appendPlanSection(
        builder,
        "NEXT ITEMS",
        filterByStatus(orderedTasks, TaskStatus.TODO),
        MAX_SECTION_ITEMS,
        true,
        false,
        title,
        tasksById,
        taskVsAncestors);
    appendPlanSection(
        builder,
        "RECENT COMPLETED",
        filterByStatus(orderedTasks, TaskStatus.DONE),
        MAX_SECTION_ITEMS,
        false,
        true,
        title,
        tasksById,
        taskVsAncestors);

    builder.append("\nPlan Tree (compact):\n");
    appendTaskTree(builder, taskVsChildren, null, 0);

    return builder.toString().trim();
  }

  public static boolean hasOpenTask(final Plan plan) {
    return CollectionUtils.isNotEmpty(collectOpenTasks(plan));
  }

  public static String buildTaskFocusPrompt(final Plan plan) {
    final List<Task> openTasks = collectOpenTasks(plan);
    if (CollectionUtils.isEmpty(openTasks)) {
      return "";
    }
    final StringBuilder builder = new StringBuilder();
    builder.append("TASK LOOP: Continue until all tasks are done or abandoned. ")
        .append("Use update_task for progress and add_task to add new work.\n");
    builder.append("FOCUS TASKS:\n");
    final int limit = Math.min(openTasks.size(), MAX_FOCUS_TASKS);
    for (int i = 0; i < limit; i++) {
      builder.append("- ").append(formatTaskFocus(openTasks.get(i))).append("\n");
    }
    return builder.toString().trim();
  }

  public static Plan getPlanFromContext(final InvocationContext context) {
    if (context == null) {
      return null;
    }
    final Session session = context.session();
    if (session == null || session.state() == null) {
      return null;
    }
    return getPlanFromState(session.state());
  }

  public static Task getOpenTask(final Plan plan) {
    return CollectionUtils.getFirst(collectOpenTasks(plan));
  }

  private static void appendProgressSummary(
      final StringBuilder builder, final List<Task> tasks) {
    if (CollectionUtils.isEmpty(tasks)) {
      builder.append("Progress: No subtasks defined yet.\n");
      return;
    }
    final int total = tasks.size();
    final int completed = countByStatus(tasks, TaskStatus.DONE);
    final int inProgress = countByStatus(tasks, TaskStatus.IN_PROGRESS);
    final int todo = countByStatus(tasks, TaskStatus.TODO);
    final int abandoned = countByStatus(tasks, TaskStatus.ABANDONED);
    builder
        .append("Progress: ")
        .append(completed)
        .append("/")
        .append(total)
        .append(" completed (")
        .append(inProgress)
        .append(" in progress, ")
        .append(todo)
        .append(" pending");

    if (abandoned > 0) {
      builder.append(", ").append(abandoned).append(" abandoned");
    }
    builder.append(")\n");
  }

  private static int countByStatus(final List<Task> tasks, final TaskStatus status) {
    return (int) tasks.stream().filter(task -> getTaskStatusEnum(task) == status).count();
  }

  private static List<Task> filterByStatus(
      final List<Task> tasks, final TaskStatus status) {
    if (CollectionUtils.isEmpty(tasks)) {
      return List.of();
    }
    return tasks.stream()
        .filter(task -> getTaskStatusEnum(task) == status)
        .collect(Collectors.toList());
  }

  private static void appendPlanSection(
      final StringBuilder builder,
      final String title,
      final List<Task> items,
      final int maxItems,
      final boolean includeGoal,
      final boolean includeResult,
      final String rootTitle,
      final Map<String, Task> tasksById,
      final Map<String, List<Task>> ancestorMap) {
    if (CollectionUtils.isEmpty(items)) {
      return;
    }
    builder.append(title).append(":\n");
    final int limit = Math.min(items.size(), maxItems);
    for (int i = 0; i < limit; i++) {
      builder
          .append("- ")
          .append(formatTaskLine(items.get(i), includeGoal, includeResult, rootTitle, tasksById, ancestorMap))
          .append("\n");
    }
    if (items.size() > maxItems) {
      builder.append("- ").append(items.size() - maxItems).append(" more items not shown\n");
    }
  }

  private static void appendTaskTree(
      final StringBuilder sb,
      final Map<String, List<Task>> taskTree,
      final String parentId,
      final int depth) {
    final List<Task> level = CollectionUtils.nullSafeList(taskTree.get(parentId));
    for (Task task : level) {
      sb.append("  ".repeat(Math.max(0, depth)))
          .append("- ")
          .append(taskLabel(task))
          .append("\n");
      appendTaskTree(sb, taskTree, task.getTaskId(), depth + 1);
    }
  }

  private static String taskLabel(final Task task) {
    final String name = getTaskName(task);
    final String id = getTaskId(task);
    final String parentId = getTaskParent(task);
    return "[" + getTaskStatus(task) + "] " + name + " (id: " + id + ", parent: " + parentId + ")";
  }

  private static String planTitle(final Plan plan) {
    if (plan == null) {
      return "Untitled";
    }
    return StringUtils.isBlank(plan.getTitle()) ? "Untitled" : plan.getTitle();
  }

  static List<Task> collectOrderedTasks(final Map<String, List<Task>> taskTree) {
    final List<Task> ordered = new ArrayList<>();
    appendOrderedTasks(taskTree, null, ordered);
    return ordered;
  }

  private static void appendOrderedTasks(
      final Map<String, List<Task>> taskTree, final String parentId, final List<Task> ordered) {
    for (Task task : CollectionUtils.nullSafeList(taskTree.get(parentId))) {
      ordered.add(task);
      appendOrderedTasks(taskTree, task.getTaskId(), ordered);
    }
  }

  private static String formatTaskLine(
      final Task task,
      final boolean includeGoal,
      final boolean includeResult,
      final String rootTitle,
      final Map<String, Task> tasksById,
      final Map<String, List<Task>> ancestorMap) {
    final List<String> path = new ArrayList<>();
    path.add(rootTitle);
    for (Task ancestor : getAncestors(task, tasksById, ancestorMap)) {
      path.add(getTaskName(ancestor));
    }
    path.add(getTaskName(task));
    final StringBuilder line = new StringBuilder();
    line.append(String.join(" > ", path));
    if (StringUtils.isNotBlank(task.getTaskId())) {
      line.append(" (id: ").append(task.getTaskId());
      line.append(", parent: ").append(getTaskParent(task));
      line.append(")");
    }
    line.append(" [").append(getTaskStatus(task)).append("]");
    if (includeGoal && StringUtils.isNotBlank(task.getGoal())) {
      line.append(" | goal: ").append(task.getGoal());
    }
    if (includeResult && StringUtils.isNotBlank(task.getResult())) {
      line.append(" | result: ").append(task.getResult());
    }
    return line.toString();
  }

  public static Plan getCurrentPlan(final ToolContext toolContext) {
    if (toolContext == null) {
      LOG.warn("toolContext is missing for plan access");
      return null;
    }
    return getPlanFromState(toolContext.state());
  }

  private static Plan getPlanFromState(final Map<String, Object> state) {
    if (CollectionUtils.isEmpty(state)) {
      return null;
    }
    final Object value = state.get(PLAN_STATE_KEY);
    if (value instanceof Plan plan) {
      return plan;
    }
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      final Map<String, Object> stringMap = (Map<String, Object>) map;
      return JsonUtils.fromMap(stringMap, Plan.class);
    }
    return null;
  }

  public static void savePlan(final ToolContext toolContext, final Plan plan) {
    if (toolContext == null) {
      LOG.warn("toolContext is missing for plan save");
      return;
    }
    toolContext.state().put(PLAN_STATE_KEY, plan);
  }

  public static void savePlan(final InvocationContext context, final Plan plan) {
    if (context == null || context.session() == null || context.session().state() == null) {
      LOG.warn("Invocation context is missing for plan save");
      return;
    }
    context.session().state().put(PLAN_STATE_KEY, plan);
  }

  public static Task findTaskById(final Plan plan, final String taskId) {
    if (plan == null || plan.getTasks() == null || StringUtils.isBlank(taskId)) {
      return null;
    }
    for (Task task : plan.getTasks()) {
      if (taskId.equals(task.getTaskId())) {
        return task;
      }
    }
    return null;
  }

  private static boolean isOpenTask(final Task task) {
    if (task == null) {
      return false;
    }
    final TaskStatus status = task.getStatus();
    return status == null
        || status == TaskStatus.TODO
        || status == TaskStatus.IN_PROGRESS
        || status == TaskStatus.UNKNOWN;
  }

  private static List<Task> collectOpenTasks(final Plan plan) {
    if (plan == null) {
      return List.of();
    }
    final PlanStatus status = plan.getStatus();
    if (status != null && status != PlanStatus.IN_PROGRESS && status != PlanStatus.UNKNOWN) {
      return List.of();
    }
    final Map<String, List<Task>> taskTree = groupTasksByParent(plan);
    final List<Task> orderedTasks = collectOrderedTasks(taskTree);
    if (CollectionUtils.isEmpty(orderedTasks)) {
      return List.of();
    }
    final List<Task> openTasks = orderedTasks.stream().filter(PlanningUtils::isOpenTask).toList();
    if (CollectionUtils.isEmpty(openTasks)) {
      return List.of();
    }
    Task nextTodo = null;
    for (Task task : orderedTasks) {
      if (getTaskStatusEnum(task) == TaskStatus.TODO) {
        nextTodo = task;
        break;
      }
    }
    if (nextTodo == null) {
      return openTasks;
    }
    final String nextId = getTaskIdValue(nextTodo);
    if (StringUtils.isBlank(nextId)) {
      return openTasks;
    }
    final Map<String, Task> tasksById = getTasksById(plan);
    final Map<String, List<Task>> ancestorMap = buildTaskAncestorMap(plan, tasksById);
    final List<Task> branchTasks =
        openTasks.stream()
            .filter(task -> isInSubtree(task, nextId, tasksById, ancestorMap))
            .toList();
    return CollectionUtils.isEmpty(branchTasks) ? openTasks : branchTasks;
  }

  private static String formatTaskFocus(final Task task) {
    final String status = getTaskStatus(task);
    final String name = getTaskName(task);
    final String id = getTaskId(task);
    final String parent = getTaskParent(task);
    final StringBuilder line = new StringBuilder();
    line.append("[").append(status).append("] ").append(name);
    line.append(" (id: ").append(id).append(", parent: ").append(parent).append(")");
    if (StringUtils.isNotBlank(task.getGoal())) {
      line.append(" | goal: ").append(task.getGoal());
    }
    if (StringUtils.isNotBlank(task.getDescription())) {
      line.append(" | description: ").append(task.getDescription());
    }
    return line.toString();
  }

  private static String getTaskParent(final Task task) {
    return StringUtils.isBlank(task.getParentId()) ? "none" : task.getParentId();
  }

  private static String getTaskId(final Task task) {
    return StringUtils.isBlank(task.getTaskId()) ? "no-id" : task.getTaskId();
  }

  private static String getTaskName(final Task task) {
    return StringUtils.isBlank(task.getName()) ? "Untitled" : task.getName();
  }

  private static String getTaskStatus(final Task task) {
    return getTaskStatusEnum(task).getValue();
  }

  static TaskStatus getTaskStatusEnum(final Task task) {
    if (task == null || task.getStatus() == null) {
      return TaskStatus.TODO;
    }
    return task.getStatus();
  }

  private static String getPlanStatus(final Plan plan) {
    if (plan == null || plan.getStatus() == null) {
      return PlanStatus.IN_PROGRESS.getValue();
    }
    return plan.getStatus().getValue();
  }

  static Map<String, List<Task>> groupTasksByParent(final Plan plan) {
    if (plan == null) {
      return Map.of();
    }
    final Map<String, List<Task>> grouped = new LinkedHashMap<>();
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      grouped.computeIfAbsent(task.getParentId(), ignored -> new ArrayList<>()).add(task);
    }
    return grouped;
  }

  public static Map<String, Task> getTasksById(final Plan plan) {
    if (plan == null) {
      return Map.of();
    }
    final Map<String, Task> tasksById = new LinkedHashMap<>();
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      final String taskId = getTaskIdValue(task);
      if (StringUtils.isNotBlank(taskId)) {
        tasksById.putIfAbsent(taskId, task);
      }
    }
    return tasksById;
  }

  static Map<String, List<Task>> buildTaskAncestorMap(
      final Plan plan, final Map<String, Task> tasksById) {
    if (plan == null) {
      return Map.of();
    }
    final Map<String, List<Task>> ancestorMap = new LinkedHashMap<>();
    final Map<String, List<Task>> taskTree = groupTasksByParent(plan);
    final Set<String> visited = new HashSet<>();
    final List<Task> roots = new ArrayList<>();
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      final String parentId = task.getParentId();
      if (StringUtils.isBlank(parentId) || !tasksById.containsKey(parentId)) {
        roots.add(task);
      }
    }
    for (Task root : roots) {
      appendAncestorMap(root, List.of(), taskTree, ancestorMap, visited);
    }
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      appendAncestorMap(task, resolveAncestors(task.getParentId(), tasksById), taskTree, ancestorMap, visited);
    }
    return ancestorMap;
  }

  static List<Task> getAncestors(
      final Task task, final Map<String, Task> tasksById, final Map<String, List<Task>> ancestorMap) {
    final String taskId = getTaskIdValue(task);
    if (StringUtils.isNotBlank(taskId)) {
      final List<Task> cached = ancestorMap.get(taskId);
      if (cached != null) {
        return cached;
      }
    }
    return resolveAncestors(task == null ? null : task.getParentId(), tasksById);
  }

  private static List<Task> resolveAncestors(
      final String parentId, final Map<String, Task> tasksById) {
    final List<Task> ancestors = new ArrayList<>();
    final Set<String> visited = new HashSet<>();
    String currentId = parentId;
    while (StringUtils.isNotBlank(currentId) && visited.add(currentId)) {
      final Task parent = tasksById.get(currentId);
      if (parent == null) {
        break;
      }
      ancestors.addFirst(parent);
      currentId = parent.getParentId();
    }
    return ancestors;
  }

  private static void appendAncestorMap(
      final Task task,
      final List<Task> ancestors,
      final Map<String, List<Task>> taskTree,
      final Map<String, List<Task>> ancestorMap,
      final Set<String> visited) {
    if (task == null) {
      return;
    }
    final String taskId = getTaskIdValue(task);
    if (StringUtils.isNotBlank(taskId)) {
      if (!visited.add(taskId)) {
        return;
      }
      ancestorMap.put(taskId, List.copyOf(ancestors));
    }
    final List<Task> nextAncestors = new ArrayList<>(ancestors);
    nextAncestors.add(task);
    for (Task child : CollectionUtils.nullSafeList(taskTree.get(taskId))) {
      appendAncestorMap(child, nextAncestors, taskTree, ancestorMap, visited);
    }
  }

  private static boolean isInSubtree(
      final Task task,
      final String ancestorId,
      final Map<String, Task> tasksById,
      final Map<String, List<Task>> ancestorMap) {
    if (StringUtils.isBlank(ancestorId)) {
      return true;
    }
    final String taskId = getTaskIdValue(task);
    if (StringUtils.isNotBlank(taskId) && ancestorId.equals(taskId)) {
      return true;
    }
    for (Task ancestor : getAncestors(task, tasksById, ancestorMap)) {
      if (ancestorId.equals(getTaskIdValue(ancestor))) {
        return true;
      }
    }
    return false;
  }

  static String getTaskIdValue(final Task task) {
    return task == null ? null : task.getTaskId();
  }
}
