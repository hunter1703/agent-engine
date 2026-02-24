package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlanningUtils {
  private static final int MAX_SECTION_ITEMS = 5;
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
    builder.append("\nStatus: ").append(plan.getStatus().getValue());
    if (StringUtils.isNotBlank(plan.getGoal())) {
      builder.append("\nGoal: ").append(plan.getGoal());
    }
    if (StringUtils.isNotBlank(plan.getResult())) {
      builder.append("\nResult: ").append(plan.getResult());
    }
    builder.append("\nTask IDs: Use the IDs below when updating tasks.");
    builder.append("\n\n");

    final List<TaskNode> taskNodes = collectTaskNodes(plan);
    appendProgressSummary(builder, taskNodes);

    appendPlanSection(
        builder,
        "CURRENT FOCUS",
        filterByStatus(taskNodes, TaskStatus.IN_PROGRESS),
        MAX_SECTION_ITEMS,
        true,
        false);
    appendPlanSection(
        builder,
        "NEXT ITEMS",
        filterByStatus(taskNodes, TaskStatus.TODO),
        MAX_SECTION_ITEMS,
        true,
        false);
    appendPlanSection(
        builder,
        "RECENT COMPLETED",
        filterByStatus(taskNodes, TaskStatus.DONE),
        MAX_SECTION_ITEMS,
        false,
        true);

    builder.append("\nPlan Tree (compact):\n");
    appendTaskTree(builder, plan.getTasks(), null, 0);

    return builder.toString().trim();
  }

  private static void appendProgressSummary(
      final StringBuilder builder, final List<TaskNode> taskNodes) {
    if (CollectionUtils.isEmpty(taskNodes)) {
      builder.append("Progress: No subtasks defined yet.\n");
      return;
    }
    final int total = taskNodes.size();
    final int completed = countByStatus(taskNodes, TaskStatus.DONE);
    final int inProgress = countByStatus(taskNodes, TaskStatus.IN_PROGRESS);
    final int todo = countByStatus(taskNodes, TaskStatus.TODO);
    final int abandoned = countByStatus(taskNodes, TaskStatus.ABANDONED);
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

  private static int countByStatus(final List<TaskNode> nodes, final TaskStatus status) {
    return (int) nodes.stream().filter(n -> n.task().getStatus() == status).count();
  }

  private static List<TaskNode> filterByStatus(
      final List<TaskNode> nodes, final TaskStatus status) {
    if (CollectionUtils.isEmpty(nodes)) {
      return Collections.emptyList();
    }
    return nodes.stream()
        .filter(n -> n.task().getStatus() == status)
        .collect(Collectors.toList());
  }

  private static void appendPlanSection(
      final StringBuilder builder,
      final String title,
      final List<TaskNode> items,
      final int maxItems,
      final boolean includeGoal,
      final boolean includeResult) {
    if (CollectionUtils.isEmpty(items)) {
      return;
    }
    builder.append(title).append(":\n");
    final int limit = Math.min(items.size(), maxItems);
    for (int i = 0; i < limit; i++) {
      builder
          .append("- ")
          .append(formatTaskNode(items.get(i), includeGoal, includeResult))
          .append("\n");
    }
    if (items.size() > maxItems) {
      builder.append("- ").append(items.size() - maxItems).append(" more items not shown\n");
    }
  }

  private static void appendTaskTree(
          final StringBuilder sb, final List<Task> allTasks, final String parentId, final int depth) {
    final List<Task> level =
        CollectionUtils.nullSafeList(allTasks).stream()
            .filter(
                t ->
                    (parentId == null && t.getParentId() == null)
                        || (parentId != null && parentId.equals(t.getParentId())))
            .collect(Collectors.toList());

    for (Task task : level) {
      sb.append("  ".repeat(Math.max(0, depth)))
          .append("- ")
          .append(taskLabel(task))
          .append("\n");
      appendTaskTree(sb, allTasks, task.getId(), depth + 1);
    }
  }

  private static String taskLabel(final Task task) {
    final String name = StringUtils.isBlank(task.getName()) ? "Untitled" : task.getName();
    final String id = StringUtils.isBlank(task.getId()) ? "no-id" : task.getId();
    final String parentId = StringUtils.isBlank(task.getParentId()) ? "none" : task.getParentId();
    return "[" + task.getStatus().getValue() + "] " + name + " (id: " + id + ", parent: " + parentId + ")";
  }

  private static String planTitle(final Plan plan) {
    if (plan == null) {
      return "Untitled";
    }
    return StringUtils.isBlank(plan.getTitle()) ? "Untitled" : plan.getTitle();
  }

  private static List<TaskNode> collectTaskNodes(final Plan plan) {
    final List<TaskNode> nodes = new ArrayList<>();
    final String rootTitle = planTitle(plan);
    appendTaskNodes(plan.getTasks(), null, List.of(rootTitle), nodes);
    return nodes;
  }

  private static void appendTaskNodes(
      final List<Task> allTasks, final String parentId, final List<String> parentPath, final List<TaskNode> nodes) {
    final List<Task> level =
        CollectionUtils.nullSafeList(allTasks).stream()
            .filter(
                t ->
                    (parentId == null && t.getParentId() == null)
                        || (parentId != null && parentId.equals(t.getParentId())))
            .collect(Collectors.toList());

    for (Task task : level) {
      final List<String> path = new ArrayList<>(parentPath);
      path.add(task.getName());
      nodes.add(new TaskNode(task, path));
      appendTaskNodes(allTasks, task.getId(), path, nodes);
    }
  }

  private static String formatTaskNode(
      final TaskNode node, final boolean includeGoal, final boolean includeResult) {
    final Task task = node.task();
    final StringBuilder line = new StringBuilder();
    line.append(String.join(" > ", node.path()));
    if (StringUtils.isNotBlank(task.getId())) {
      line.append(" (id: ").append(task.getId());
      line.append(", parent: ")
          .append(StringUtils.isBlank(task.getParentId()) ? "none" : task.getParentId());
      line.append(")");
    }
    line.append(" [").append(task.getStatus().getValue()).append("]");
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
    final Object value = toolContext.state().get(PLAN_STATE_KEY);
    if (value instanceof Plan plan) {
      return plan;
    }
    if (value instanceof Map<?, ?> map) {
      // noinspection unchecked
      return JsonUtils.fromMap((Map<String, Object>) map, Plan.class);
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

  public static Task findTaskById(final Plan plan, final String taskId) {
    if (plan == null || plan.getTasks() == null || StringUtils.isBlank(taskId)) {
      return null;
    }
    for (Task task : plan.getTasks()) {
      if (taskId.equals(task.getId())) {
        return task;
      }
    }
    return null;
  }

  private record TaskNode(Task task, List<String> path) {}
}
