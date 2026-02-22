package com.agentengine.engine.tools.planning;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlanningUtils {
  private static final int MAX_SECTION_ITEMS = 5;
  private static final Logger LOG = LoggerFactory.getLogger(PlanningUtils.class);
  public static final String PLAN_STATE_KEY = "currentPlan";

  private PlanningUtils() {}

  public static String buildPlanSummary(final Plan plan) {
    if (plan == null) {
      return "";
    }
    final StringBuilder builder = new StringBuilder();
    builder.append("PLAN CONTEXT\n");
    builder.append("Plan: ").append(planName(plan));
    if (StringUtils.isNotBlank(plan.getId())) {
      builder.append(" (id=").append(plan.getId()).append(")");
    }
    builder.append("\nStatus: ").append(plan.getStatus()).append("\n");
    if (StringUtils.isNotBlank(plan.getDescription())) {
      builder.append("Description: ").append(plan.getDescription()).append("\n");
    }
    if (StringUtils.isNotBlank(plan.getExpectedOutcome())) {
      builder.append("Expected Outcome: ").append(plan.getExpectedOutcome()).append("\n");
    }
    if (StringUtils.isNotBlank(plan.getOutcome())) {
      builder.append("Outcome: ").append(plan.getOutcome()).append("\n");
    }

    final List<PlanNode> planNodes = collectPlanNodes(plan);
    appendProgressSummary(builder, planNodes);

    appendPlanSection(
        builder,
        "CURRENT FOCUS",
        filterByStatus(planNodes, PlanStatus.IN_PROGRESS),
        MAX_SECTION_ITEMS,
        true,
        false);
    appendPlanSection(
        builder,
        "NEXT ITEMS",
        filterByStatus(planNodes, PlanStatus.TODO),
        MAX_SECTION_ITEMS,
        true,
        false);
    appendPlanSection(
        builder,
        "RECENT COMPLETED",
        filterByStatus(planNodes, PlanStatus.DONE),
        MAX_SECTION_ITEMS,
        false,
        true);

    builder.append("Plan Tree (compact):\n");
    appendPlanTree(builder, plan, 0);
    return builder.toString().trim();
  }

  private static void appendProgressSummary(
      final StringBuilder builder, final List<PlanNode> planNodes) {
    if (CollectionUtils.isEmpty(planNodes)) {
      builder.append("Progress: No subtasks defined yet.\n");
      return;
    }
    final int total = planNodes.size();
    final int completed = countByStatus(planNodes, PlanStatus.DONE);
    final int inProgress = countByStatus(planNodes, PlanStatus.IN_PROGRESS);
    final int todo = countByStatus(planNodes, PlanStatus.TODO);
    final int abandoned = countByStatus(planNodes, PlanStatus.ABANDONED);
    builder
        .append("Progress: ")
        .append(completed)
        .append("/")
        .append(total)
        .append(" done, ")
        .append(inProgress)
        .append(" in progress, ")
        .append(todo)
        .append(" todo, ")
        .append(abandoned)
        .append(" abandoned\n");
  }

  private static int countByStatus(final List<PlanNode> planNodes, final PlanStatus status) {
    int count = 0;
    for (PlanNode planNode : CollectionUtils.nullSafeList(planNodes)) {
      if (planNode.plan().getStatus() == status) {
        count++;
      }
    }
    return count;
  }

  private static void appendPlanSection(
      final StringBuilder builder,
      final String title,
      final List<PlanNode> items,
      final int maxItems,
      final boolean includeExpected,
      final boolean includeOutcome) {
    if (CollectionUtils.isEmpty(items)) {
      return;
    }
    builder.append(title).append(":\n");
    final int limit = Math.min(items.size(), maxItems);
    for (int i = 0; i < limit; i++) {
      builder
          .append("- ")
          .append(formatPlanNode(items.get(i), includeExpected, includeOutcome))
          .append("\n");
    }
    if (items.size() > maxItems) {
      builder.append("- ").append(items.size() - maxItems).append(" more items not shown\n");
    }
  }

  private static List<PlanNode> filterByStatus(
      final List<PlanNode> nodes, final PlanStatus status) {
    if (CollectionUtils.isEmpty(nodes)) {
      return Collections.emptyList();
    }
    final List<PlanNode> matches = new ArrayList<>();
    for (PlanNode node : nodes) {
      if (node.plan().getStatus() == status) {
        matches.add(node);
      }
    }
    return matches;
  }

  private static void appendPlanTree(
      final StringBuilder builder, final Plan plan, final int depth) {
    if (plan == null) {
      return;
    }
    builder
        .append("  ".repeat(Math.max(0, depth)))
        .append("- ")
        .append(planLabel(plan))
        .append("\n");
    for (Plan subtask : CollectionUtils.nullSafeList(plan.getSubtasks())) {
      appendPlanTree(builder, subtask, depth + 1);
    }
  }

  private static String planLabel(final Plan plan) {
    final String name = StringUtils.isBlank(plan.getName()) ? "Untitled" : plan.getName();
    final String id = StringUtils.isBlank(plan.getId()) ? "" : " (id=" + plan.getId() + ")";
    return STR."\{name} [\{plan.getStatus()}]\{id}";
  }

  private static String planName(final Plan plan) {
    if (plan == null) {
      return "Untitled";
    }
    return StringUtils.isBlank(plan.getName()) ? "Untitled" : plan.getName();
  }

  private static List<PlanNode> collectPlanNodes(final Plan plan) {
    final List<PlanNode> nodes = new ArrayList<>();
    final List<String> rootPath = List.of(planName(plan));
    appendPlanNodes(plan.getSubtasks(), rootPath, nodes);
    return nodes;
  }

  private static void appendPlanNodes(
      final List<Plan> plans, final List<String> parentPath, final List<PlanNode> nodes) {
    if (CollectionUtils.isEmpty(plans)) {
      return;
    }
    for (Plan planItem : plans) {
      if (planItem == null) {
        continue;
      }
      final List<String> path = new ArrayList<>(parentPath);
      path.add(planName(planItem));
      nodes.add(new PlanNode(planItem, path));
      appendPlanNodes(planItem.getSubtasks(), path, nodes);
    }
  }

  private static String formatPlanNode(
      final PlanNode node, final boolean includeExpected, final boolean includeOutcome) {
    final Plan plan = node.plan();
    final StringBuilder line = new StringBuilder();
    line.append(String.join(" > ", node.path()));
    if (StringUtils.isNotBlank(plan.getId())) {
      line.append(" (id=").append(plan.getId()).append(")");
    }
    line.append(" [").append(plan.getStatus()).append("]");
    if (includeExpected && StringUtils.isNotBlank(plan.getExpectedOutcome())) {
      line.append(" | expected: ").append(plan.getExpectedOutcome());
    }
    if (includeOutcome && StringUtils.isNotBlank(plan.getOutcome())) {
      line.append(" | outcome: ").append(plan.getOutcome());
    }
    return line.toString();
  }

  public static List<Plan> toPlans(final List<Map<String, Object>> subtasksList) {
    final List<Map<String, Object>> safeSubtasksList =
        CollectionUtils.nullSafeMutableList(subtasksList);
    final List<Plan> subtasks = new ArrayList<>();
    for (final Map<String, Object> subtaskProps : safeSubtasksList) {
      final String name = CollectionUtils.getStringValueFromMapSafe(subtaskProps, "name");
      final String description =
          CollectionUtils.getStringValueFromMapSafe(subtaskProps, "description");
      final String expectedOutcome =
          CollectionUtils.getStringValueFromMapSafe(subtaskProps, "expected_outcome");
      final Plan subtask = new Plan(name, description, expectedOutcome);
      final String planId = resolvePlanId(subtaskProps);
      if (StringUtils.isNotBlank(planId)) {
        subtask.setId(planId);
      }
      final List<Map<String, Object>> nestedSubtasks =
          CollectionUtils.getListFromMap(subtaskProps, "subtasks");
      if (CollectionUtils.isNotEmpty(nestedSubtasks)) {
        subtask.setSubtasks(toPlans(nestedSubtasks));
      }
      subtasks.add(subtask);
    }
    return subtasks;
  }

  public static Plan getCurrentPlan(final ToolContext toolContext) {
    if (toolContext == null) {
      LOG.warn("toolContext is missing for plan access");
      return null;
    }
    return CollectionUtils.getValueFromMap(toolContext.state(), PLAN_STATE_KEY, Plan.class);
  }

  public static void savePlan(final ToolContext toolContext, final Plan plan) {
    if (toolContext == null) {
      LOG.warn("toolContext is missing for plan save");
      return;
    }
    toolContext.state().put(PLAN_STATE_KEY, plan);
  }

  static Plan findPlanById(final Plan plan, final String planId) {
    if (plan == null || StringUtils.isBlank(planId)) {
      return null;
    }
    if (planId.equals(plan.getId())) {
      return plan;
    }
    return _findPlanById(plan.getSubtasks(), planId);
  }

  private static Plan _findPlanById(final List<Plan> planItems, final String planId) {
    if (planItems == null || StringUtils.isBlank(planId)) {
      return null;
    }
    for (final Plan planItem : planItems) {
      final Plan match = findPlanById(planItem, planId);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  private static String resolvePlanId(final Map<String, Object> planArgsItem) {
    return CollectionUtils.getStringValueFromMapSafe(planArgsItem, "plan_id");
  }

  private record PlanNode(Plan plan, List<String> path) {}
}
