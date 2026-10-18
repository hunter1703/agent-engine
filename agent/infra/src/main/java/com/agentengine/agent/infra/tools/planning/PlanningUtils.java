package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PlanningUtils {
  private static final int MAX_SECTION_ITEMS = 5;
  private static final int MAX_FOCUS_TASKS = 1;

  private PlanningUtils() {}

  /**
   * Builds a tree-like string representation of the root plan and its current task hierarchy. This
   * summary is injected into the LLM context to help the agent track progress.
   *
   * <p>Example prompt output:
   *
   * <p>PLAN CONTEXT Title: Refactor API layer Status: in_progress Goal: Migrate all endpoints to
   * flat Task models to resolve recursion issues. Task IDs: Use the IDs below when updating tasks.
   *
   * <p>Progress: 1/5 completed (1 in progress, 3 pending)
   *
   * <p>CURRENT FOCUS: - Refactor API layer > Update Plan bean (id: task-2, parent: none)
   * [in_progress] | goal: Update Plan model
   *
   * <p>NEXT ITEMS: - Refactor API layer > Define Task bean (id: task-1, parent: none) [todo] |
   * goal: Create Task model - Refactor API layer > Remove subtasks field (id: task-3, parent:
   * task-2) [todo] | goal: Drop recursion - Refactor API layer > Add tasks field (id: task-4,
   * parent: task-2) [todo] | goal: Flat list
   *
   * <p>RECENT COMPLETED: - Refactor API layer > Verify llama-server fix (id: task-5, parent: none)
   * [done] | result: Smoke tested
   *
   * <p>Plan Tree (compact): - [todo] Define Task bean (id: task-1, parent: none) - [in_progress]
   * Update Plan bean (id: task-2, parent: none) - [todo] Remove subtasks field (id: task-3, parent:
   * task-2) - [todo] Add tasks field (id: task-4, parent: task-2) - [done] Verify llama-server
   * recursion fix (id: task-5, parent: none)
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
    builder.append("\n\n");

    final Map<String, Task> nameVsTask = getTasksByName(plan);
    final Map<String, List<Task>> taskVsAncestors = buildTaskAncestorMap(plan, nameVsTask);
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
        nameVsTask,
        taskVsAncestors);
    appendPlanSection(
        builder,
        "NEXT ITEMS",
        filterByStatus(orderedTasks, TaskStatus.TODO),
        MAX_SECTION_ITEMS,
        true,
        false,
        title,
        nameVsTask,
        taskVsAncestors);
    appendPlanSection(
        builder,
        "RECENT COMPLETED",
        filterByStatus(orderedTasks, TaskStatus.DONE),
        MAX_SECTION_ITEMS,
        false,
        true,
        title,
        nameVsTask,
        taskVsAncestors);

    builder.append("\nPlan Tree (compact):\n");
    final Set<String> relevantIds = collectRelevantTaskIds(plan, nameVsTask, taskVsAncestors);
    appendTaskTree(builder, taskVsChildren, null, 0, relevantIds);

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
    builder.append("TASK LOOP: Continue until all tasks are done or abandoned. ");
    builder.append("FOCUS TASKS:\n");
    final int limit = Math.min(openTasks.size(), MAX_FOCUS_TASKS);
    for (int i = 0; i < limit; i++) {
      builder.append("- ").append(formatTaskFocus(openTasks.get(i))).append("\n");
    }
    return builder.toString().trim();
  }

  public static Task getOpenTask(final Plan plan) {
    return CollectionUtils.getFirst(collectOpenTasks(plan));
  }

  /**
   * Lean, persistent per-turn reminder of the active plan: title, progress, and the single next
   * actionable task. Deliberately omits {@link #buildPlanSummary}'s full task list and tree —
   * that's a lot of text to repeat on every turn for the plan's whole duration when only the next
   * action actually drives the next decision. The full picture (every task, hierarchy, results) is
   * always one {@link ViewPlanTool} call away, not force-fed every turn.
   */
  public static String activePlanBrief(final Plan plan) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Plan '")
        .append(planTitle(plan))
        .append("' (")
        .append(getPlanStatus(plan))
        .append(")");
    if (StringUtils.isNotBlank(plan.getGoal())) {
      sb.append(" — ").append(plan.getGoal());
    }
    sb.append("\n");

    final Map<String, List<Task>> taskVsChildren = groupTasksByParent(plan);
    appendProgressSummary(sb, collectOrderedTasks(taskVsChildren));
    sb.append("Full task list and hierarchy: ")
        .append(ViewPlanTool.DESCRIPTOR.name())
        .append(".\n");

    final Task openTask = getOpenTask(plan);
    if (openTask != null) {
      sb.append("\nActive task — stay focused on this:\n");
      sb.append(buildTaskFocusPrompt(plan));
      sb.append(
          "\n **Do not start a new task until this one is complete or explicitly abandoned.**");
    } else {
      final Task nextTask = findNextTodoTask(plan);
      if (nextTask != null) {
        sb.append("\nNo active task — pick up the next one:\n");
        sb.append("Task '").append(nextTask.getName()).append("'");
        if (StringUtils.isNotBlank(nextTask.getGoal())) {
          sb.append("\nGoal: ").append(nextTask.getGoal());
        }
        sb.append("\n→ **Mark it in_progress before starting work.**");
      }
    }
    return sb.toString().trim();
  }

  private static void appendProgressSummary(final StringBuilder builder, final List<Task> tasks) {
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

  private static List<Task> filterByStatus(final List<Task> tasks, final TaskStatus status) {
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
      final Map<String, Task> nameVsTask,
      final Map<String, List<Task>> ancestorMap) {
    if (CollectionUtils.isEmpty(items)) {
      return;
    }
    builder.append(title).append(":\n");
    final int limit = Math.min(items.size(), maxItems);
    for (int i = 0; i < limit; i++) {
      builder
          .append("- ")
          .append(
              formatTaskLine(
                  items.get(i), includeGoal, includeResult, rootTitle, nameVsTask, ancestorMap))
          .append("\n");
    }
    if (items.size() > maxItems) {
      builder.append("- ").append(items.size() - maxItems).append(" more items not shown\n");
    }
  }

  private static void appendTaskTree(
      final StringBuilder sb,
      final Map<String, List<Task>> taskTree,
      final String parent,
      final int depth,
      final Set<String> relevantNames) {
    final List<Task> level = CollectionUtils.nullSafeList(taskTree.get(parent));
    for (Task task : level) {
      final String taskName = task.getName();
      sb.repeat("  ", Math.max(0, depth)).append("- ").append(taskLabel(task));

      final List<Task> children = taskTree.get(taskName);
      if (CollectionUtils.isNotEmpty(children)) {
        if (depth == 0 || relevantNames.contains(taskName)) {
          sb.append("\n");
          appendTaskTree(sb, taskTree, taskName, depth + 1, relevantNames);
        } else {
          sb.append(" (+ ").append(countAllDescendants(taskName, taskTree)).append(" subtasks)\n");
        }
      } else {
        sb.append("\n");
      }
    }
  }

  private static int countAllDescendants(
      final String parent, final Map<String, List<Task>> taskTree) {
    int count = 0;
    final List<Task> children = taskTree.get(parent);
    if (children != null) {
      count += children.size();
      for (Task child : children) {
        count += countAllDescendants(child.getName(), taskTree);
      }
    }
    return count;
  }

  private static Set<String> collectRelevantTaskIds(
      final Plan plan,
      final Map<String, Task> nameVsTask,
      final Map<String, List<Task>> ancestorMap) {
    final Set<String> relevant = new HashSet<>();
    final List<Task> inProgress =
        plan.getTasks().stream()
            .filter(task -> getTaskStatusEnum(task) == TaskStatus.IN_PROGRESS)
            .toList();

    for (Task task : inProgress) {
      final String name = task.getName();
      if (name != null) {
        relevant.add(name);
        for (Task ancestor : getAncestors(task, nameVsTask, ancestorMap)) {
          final String aid = ancestor.getName();
          if (aid != null) {
            relevant.add(aid);
          }
        }
      }
    }
    return relevant;
  }

  private static String taskLabel(final Task task) {
    final String name = getTaskName(task);
    final String parent = getTaskParent(task);
    return "["
        + getTaskStatusValue(task)
        + "] "
        + name
        + " (parent: "
        + parent
        + ")";
  }

  private static String planTitle(final Plan plan) {
    if (plan == null) {
      return "Untitled";
    }
    return StringUtils.isBlank(plan.getTitle()) ? "Untitled" : plan.getTitle();
  }

  public static List<Task> collectOrderedTasks(final Map<String, List<Task>> taskTree) {
    final List<Task> ordered = new ArrayList<>();
    appendOrderedTasks(taskTree, null, ordered);
    return ordered;
  }

  private static void appendOrderedTasks(
      final Map<String, List<Task>> taskTree, final String parent, final List<Task> ordered) {
    for (Task task : CollectionUtils.nullSafeList(taskTree.get(parent))) {
      ordered.add(task);
      appendOrderedTasks(taskTree, task.getName(), ordered);
    }
  }

  private static String formatTaskLine(
      final Task task,
      final boolean includeGoal,
      final boolean includeResult,
      final String rootTitle,
      final Map<String, Task> nameVsTask,
      final Map<String, List<Task>> ancestorMap) {
    final List<String> path = new ArrayList<>();
    path.add(rootTitle);
    for (Task ancestor : getAncestors(task, nameVsTask, ancestorMap)) {
      path.add(getTaskName(ancestor));
    }
    path.add(getTaskName(task));
    final StringBuilder line = new StringBuilder();
    line.append(String.join(" > ", path));
    if (StringUtils.isNotBlank(task.getParent())) {
      line.append(" (parent: ").append(task.getParent()).append(")");
    }
    line.append(" [").append(getTaskStatusValue(task)).append("]");
    if (includeGoal && StringUtils.isNotBlank(task.getGoal())) {
      line.append(" | goal: ").append(task.getGoal());
    }
    if (includeResult && StringUtils.isNotBlank(task.getResult())) {
      line.append(" | result: ").append(task.getResult());
    }
    return line.toString();
  }

  public static Task findTaskByName(final Plan plan, final String name) {
    if (plan == null || plan.getTasks() == null || StringUtils.isBlank(name)) {
      return null;
    }
    final String trimmed = name.trim();
    for (final Task task : plan.getTasks()) {
      if (trimmed.equalsIgnoreCase(task.getName())) {
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
    final String nextName = nextTodo.getName();
    if (StringUtils.isBlank(nextName)) {
      return openTasks;
    }
    final Map<String, Task> nameVsTask = getTasksByName(plan);
    final Map<String, List<Task>> ancestorMap = buildTaskAncestorMap(plan, nameVsTask);
    final List<Task> branchTasks =
        openTasks.stream()
            .filter(task -> isInSubtree(task, nextName, nameVsTask, ancestorMap))
            .toList();
    return CollectionUtils.isEmpty(branchTasks) ? openTasks : branchTasks;
  }

  private static String formatTaskFocus(final Task task) {
    final String status = getTaskStatusValue(task);
    final String name = getTaskName(task);
    final String parent = getTaskParent(task);
    final StringBuilder line = new StringBuilder();
    line.append("[").append(status).append("] ").append(name);
    if (!"none".equals(parent)) {
      line.append(" (parent: ").append(parent).append(")");
    }
    if (StringUtils.isNotBlank(task.getGoal())) {
      line.append(" | goal: ").append(task.getGoal());
    }
    if (StringUtils.isNotBlank(task.getDescription())) {
      line.append(" | description: ").append(task.getDescription());
    }
    return line.toString();
  }

  public static String taskName(final Task task) {
    return task == null ? null : task.getName();
  }

  private static String getTaskParent(final Task task) {
    return task == null || StringUtils.isBlank(task.getParent()) ? "none" : task.getParent();
  }

  private static String getTaskName(final Task task) {
    final String name = taskName(task);
    return StringUtils.isBlank(name) ? "Untitled" : name;
  }

  public static String getTaskStatusValue(final Task task) {
    return getTaskStatusEnum(task).getValue();
  }

  public static TaskStatus getTaskStatusEnum(final Task task) {
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

  public static Map<String, List<Task>> groupTasksByParent(final Plan plan) {
    if (plan == null) {
      return Map.of();
    }
    final Map<String, List<Task>> grouped = new LinkedHashMap<>();
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      grouped.computeIfAbsent(task.getParent(), ignored -> new ArrayList<>()).add(task);
    }
    return grouped;
  }

  public static Map<String, Task> getTasksByName(final Plan plan) {
    if (plan == null) {
      return Map.of();
    }
    final Map<String, Task> tasksByName = new LinkedHashMap<>();
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      if (StringUtils.isNotBlank(task.getName())) {
        tasksByName.putIfAbsent(task.getName(), task);
      }
    }
    return tasksByName;
  }

  public static Map<String, List<Task>> buildTaskAncestorMap(
      final Plan plan, final Map<String, Task> nameVsTask) {
    if (plan == null) {
      return Map.of();
    }
    final Map<String, List<Task>> ancestorMap = new LinkedHashMap<>();
    final Map<String, List<Task>> taskTree = groupTasksByParent(plan);
    final Set<String> visited = new HashSet<>();
    final List<Task> roots = new ArrayList<>();
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      final String parent = task.getParent();
      if (StringUtils.isBlank(parent) || !nameVsTask.containsKey(parent)) {
        roots.add(task);
      }
    }
    for (Task root : roots) {
      appendAncestorMap(root, List.of(), taskTree, ancestorMap, visited);
    }
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      appendAncestorMap(
          task, resolveAncestors(task.getParent(), nameVsTask), taskTree, ancestorMap, visited);
    }
    return ancestorMap;
  }

  public static List<Task> getAncestors(
      final Task task,
      final Map<String, Task> nameVsTask,
      final Map<String, List<Task>> ancestorMap) {
    final String name = taskName(task);
    if (StringUtils.isNotBlank(name)) {
      final List<Task> cached = ancestorMap.get(name);
      if (cached != null) {
        return cached;
      }
    }
    return resolveAncestors(task == null ? null : task.getParent(), nameVsTask);
  }

  private static List<Task> resolveAncestors(
      final String parent, final Map<String, Task> nameVsTask) {
    final List<Task> ancestors = new ArrayList<>();
    final Set<String> visited = new HashSet<>();
    String current = parent;
    while (StringUtils.isNotBlank(current) && visited.add(current)) {
      final Task parentTask = nameVsTask.get(current);
      if (parentTask == null) {
        break;
      }
      ancestors.addFirst(parentTask);
      current = parentTask.getParent();
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
    final String name = task.getName();
    if (StringUtils.isNotBlank(name)) {
      if (!visited.add(name)) {
        return;
      }
      ancestorMap.put(name, List.copyOf(ancestors));
    }
    final List<Task> nextAncestors = new ArrayList<>(ancestors);
    nextAncestors.add(task);
    for (Task child : CollectionUtils.nullSafeList(taskTree.get(name))) {
      appendAncestorMap(child, nextAncestors, taskTree, ancestorMap, visited);
    }
  }

  private static boolean isInSubtree(
      final Task task,
      final String ancestorName,
      final Map<String, Task> nameVsTask,
      final Map<String, List<Task>> ancestorMap) {
    if (StringUtils.isBlank(ancestorName)) {
      return true;
    }
    final String name = taskName(task);
    if (StringUtils.isNotBlank(name) && ancestorName.equals(name)) {
      return true;
    }
    for (Task ancestor : getAncestors(task, nameVsTask, ancestorMap)) {
      if (ancestorName.equals(ancestor.getName())) {
        return true;
      }
    }
    return false;
  }

  public static Task findNextTodoTask(final Plan plan) {
    if (plan == null) {
      return null;
    }
    final Map<String, List<Task>> taskTree = groupTasksByParent(plan);
    final List<Task> orderedTasks = collectOrderedTasks(taskTree);
    for (Task task : orderedTasks) {
      final TaskStatus status = getTaskStatusEnum(task);
      if (isTerminalStatus(status) || status == TaskStatus.IN_PROGRESS) {
        continue;
      }
      return task;
    }
    return null;
  }

  public static Task findNonTerminalDescendant(
      final Task task, final Map<String, List<Task>> taskTree) {
    final String taskName = taskName(task);
    for (Task child : CollectionUtils.nullSafeList(taskTree.get(taskName))) {
      if (!isTerminalStatus(getTaskStatusEnum(child))) {
        return child;
      }
      final Task nested = findNonTerminalDescendant(child, taskTree);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  public static boolean allTasksTerminal(final Plan plan) {
    if (plan == null || CollectionUtils.isEmpty(plan.getTasks())) {
      return false;
    }
    for (Task task : plan.getTasks()) {
      if (!isTerminalStatus(getTaskStatusEnum(task))) {
        return false;
      }
    }
    return true;
  }

  public static Task findNextActionableTask(final Plan plan, final Task completedTask) {
    if (plan == null) {
      return null;
    }
    if (completedTask != null && StringUtils.isNotBlank(completedTask.getParent())) {
      final Task parentTask = findTaskByName(plan, completedTask.getParent());
      if (parentTask != null && getTaskStatusEnum(parentTask) == TaskStatus.IN_PROGRESS) {
        final Map<String, List<Task>> taskTree = groupTasksByParent(plan);
        if (findNonTerminalDescendant(parentTask, taskTree) == null) {
          return parentTask;
        }
      }
    }
    return findNextTodoTask(plan);
  }

  public static boolean isTerminalStatus(final TaskStatus status) {
    return status != null && status.isTerminal();
  }

  public static boolean isTerminalStatus(final PlanStatus status) {
    return status != null && status.isTerminal();
  }

  public static String getTerminalStatuses(final Class<? extends Enum<?>> enumClass) {
    return getStatusesByTerminalFlag(enumClass, true);
  }

  public static String getNonTerminalStatuses(final Class<? extends Enum<?>> enumClass) {
    return getStatusesByTerminalFlag(enumClass, false);
  }

  private static String getStatusesByTerminalFlag(
      final Class<? extends Enum<?>> enumClass, boolean terminal) {
    final List<String> values = new ArrayList<>();
    if (enumClass == TaskStatus.class) {
      for (TaskStatus taskStatus : TaskStatus.values()) {
        if (taskStatus != TaskStatus.UNKNOWN && taskStatus.isTerminal() == terminal) {
          values.add(taskStatus.getValue());
        }
      }
    } else if (enumClass == PlanStatus.class) {
      for (PlanStatus planStatus : PlanStatus.values()) {
        if (planStatus != PlanStatus.UNKNOWN && planStatus.isTerminal() == terminal) {
          values.add(planStatus.getValue());
        }
      }
    }
    return String.join(", ", values);
  }


  public static Plan buildFrom(final List<Event> events) {
    final Map<String, FunctionCall> pendingCallsByCallId = new HashMap<>();
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      final Content content = event.content().orElse(null);
      if (content == null) {
        continue;
      }
      for (final Part part : content.parts().orElse(List.of())) {
        final FunctionCall call = part.functionCall().orElse(null);
        if (call == null) {
          continue;
        }
        final String toolName = call.name().orElse(null);
        if (!isPlanningTool(toolName)) {
          continue;
        }
        final String callId = call.id().orElse(null);
        if (callId == null) {
          continue;
        }
        pendingCallsByCallId.put(callId, call);
      }
    }

    Plan plan = null;
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      final Content content = event.content().orElse(null);
      if (content == null) {
        continue;
      }
      for (final Part part : content.parts().orElse(List.of())) {
        final FunctionResponse response = part.functionResponse().orElse(null);
        if (response == null) {
          continue;
        }
        final String callId = response.id().orElse(null);
        final FunctionCall pendingCall = callId == null ? null : pendingCallsByCallId.get(callId);
        if (pendingCall == null) {
          continue;
        }
        final Map<String, Object> responseMap = response.response().orElse(Map.of());
        if (responseMap.containsKey("error")) {
          continue;
        }
        plan =
            applyReplayedEvent(
                plan,
                pendingCall.name().orElse(""),
                pendingCall.args().orElse(Map.of()),
                responseMap);
      }
    }
    return plan;
  }

  private static boolean isPlanningTool(final String toolName) {
    return Constants.ToolNames.CREATE_PLAN.equals(toolName)
        || Constants.ToolNames.ADD_TASK.equals(toolName)
        || Constants.ToolNames.UPDATE_TASK.equals(toolName);
  }

  private static Plan applyReplayedEvent(
      Plan currentPlan,
      final String toolName,
      final Map<String, Object> args,
      final Map<String, Object> responseMap) {
    if (Constants.ToolNames.CREATE_PLAN.equals(toolName)) {
      return JsonUtils.fromMap(
          CollectionUtils.getMapFromMap(responseMap, "createdPlan"), Plan.class);
    }
    if (Constants.ToolNames.ADD_TASK.equals(toolName)) {
      final String parent =
          CollectionUtils.getStringValueFromMap(args, Constants.ToolArgs.PARENT);
      currentPlan =
          AddTaskTool.applyAddTask(
              currentPlan,
              parent,
              CollectionUtils.getStringValueFromMap(args, "name"),
              CollectionUtils.getStringValueFromMap(args, "goal"),
              CollectionUtils.getStringValueFromMap(args, "description"));
    } else if (Constants.ToolNames.UPDATE_TASK.equals(toolName)) {
      final String task = CollectionUtils.getStringValueFromMap(args, "task");
      final String taskResult = CollectionUtils.getStringValueFromMap(args, "result");
      currentPlan =
          applyTaskUpdate(
              currentPlan,
              task,
              TaskStatus.valueOfOrDefault(CollectionUtils.getStringValueFromMap(args, "status")),
              taskResult);
    }
    return currentPlan;
  }

  public static Plan applyTaskUpdate(
      final Plan plan, final String taskName, TaskStatus newStatus, String result) {
    final Plan updatedPlan = new Plan(plan);
    final Task task = PlanningUtils.findTaskByName(updatedPlan, taskName);
    if (task == null) {
      return updatedPlan;
    }
    newStatus = newStatus != null ? newStatus : task.getStatus();
    result = StringUtils.isNotBlank(result) ? result : task.getResult();
    task.setStatus(newStatus);
    task.setResult(result);
    if (allTasksTerminal(updatedPlan)) {
      updatedPlan.finish(PlanStatus.DONE, result);
    }
    return updatedPlan;
  }
}
