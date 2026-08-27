package com.agentengine.agent.infra.utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A persistent, addressable reminder that forms part of the agent's working-memory brief.
 *
 * <p>Reminders are grouped by {@code group} for display (each group becomes a titled section) and
 * individually addressable by {@code id} for targeted removal when the condition they describe is
 * resolved (e.g. a child session is awaited, a knowledge item is no longer relevant).
 *
 * <p>Well-known groups:
 *
 * <ul>
 *   <li>{@code spawned_agents} — child sessions spawned but not yet awaited; id = child session ID
 *   <li>{@code knowledge_ids} — ids of searchable {@code Knowledge} items, each read with {@code
 *       search_knowledge}; id = the knowledge id itself
 *   <li>{@code knowledge_sources} — raw file sources with no search capability, each read whole
 *       with {@code read_knowledge_source}; id = the source string itself
 *   <li>{@code active_plan} — current plan state; id = {@link #ID_ACTIVE_PLAN}
 * </ul>
 *
 * @param group snake_case category; controls which section this appears under in the brief
 * @param id unique identifier within the group; used for targeted removal
 * @param message the item text rendered as a bullet under the group's section
 */
public record Reminder(String group, String id, String message) {

  public static final String GROUP_SPAWNED_AGENTS = "spawned_agents";
  public static final String GROUP_ACTIVE_PLAN = "active_plan";
  public static final String GROUP_KNOWLEDGE_IDS = "knowledge_ids";
  public static final String GROUP_KNOWLEDGE_SOURCES = "knowledge_sources";
  public static final String GROUP_NOTEBOOK_GRANTS = "notebook_grants";

  public static final String ID_ACTIVE_PLAN = "plan";

  /**
   * Display order for known groups in the brief, most action-critical first: unfinished plan work
   * before pending child sessions before what's merely available to use. A group not listed here
   * (future addition) sorts after all of these.
   */
  public static final List<String> GROUP_ORDER =
      List.of(
          GROUP_ACTIVE_PLAN,
          GROUP_SPAWNED_AGENTS,
          GROUP_NOTEBOOK_GRANTS,
          GROUP_KNOWLEDGE_IDS,
          GROUP_KNOWLEDGE_SOURCES);

  private static final Map<String, String> TITLES =
      Map.of(
          GROUP_ACTIVE_PLAN, "Active Plan",
          GROUP_SPAWNED_AGENTS, "Pending Child Sessions",
          GROUP_NOTEBOOK_GRANTS, "Notebook Access",
          GROUP_KNOWLEDGE_IDS, "Searchable Knowledge",
          GROUP_KNOWLEDGE_SOURCES, "Full-Text Knowledge Sources");

  /** The section title a group renders as in the brief (see {@code ReminderPlugin}). */
  public static String title(final String group) {
    return TITLES.getOrDefault(group, group.replace('_', ' '));
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Reminder reminder)) {
      return false;
    }
    return Objects.equals(group, reminder.group) && Objects.equals(id, reminder.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(group, id);
  }
}
