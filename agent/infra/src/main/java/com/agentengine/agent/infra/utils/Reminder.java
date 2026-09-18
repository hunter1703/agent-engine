package com.agentengine.agent.infra.utils;

import com.agentengine.agent.infra.tools.knowledge.ReadKnowledgeSourceTool;
import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.util.common.CollectionUtils;
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
 * <p>{@code details} is a free-form bag a reminder's own accumulation logic can use to carry
 * whatever state it needs to derive {@code message} from — e.g. a reminder that represents the
 * union of many individually-added facts (see {@code SessionState#addNotebookReminders}) can stash
 * the merged data here so a later add can read it back and merge into it, rather than each add
 * overwriting the last. Not rendered directly; {@code message} is what the brief shows.
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
 * @param details free-form state this reminder's own accumulation logic can read back; never null
 */
public record Reminder(String group, String id, String message, Map<String, Object> details) {

  public Reminder(final String group, final String id, final String message) {
    this(group, id, message, Map.of());
  }

  public Reminder {
    details = CollectionUtils.nullSafeMap(details);
  }

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

  private static final Map<String, GroupInfo> GROUPS =
      Map.of(
          GROUP_ACTIVE_PLAN, new GroupInfo("Active Plan"),
          GROUP_SPAWNED_AGENTS, new GroupInfo("Pending Child Sessions"),
          GROUP_NOTEBOOK_GRANTS, new GroupInfo("Notebook and note permissions"),
          GROUP_KNOWLEDGE_IDS,
              new GroupInfo(
                  "Available to Search",
                  "Search these with " + SearchKnowledgeTool.DESCRIPTOR.name() + "."),
          GROUP_KNOWLEDGE_SOURCES,
              new GroupInfo(
                  "Available to Read",
                  "Read these with " + ReadKnowledgeSourceTool.DESCRIPTOR.name() + "."));

  /** The section title a group renders as in the brief (see {@code ReminderPlugin}). */
  public static String title(final String group) {
    final GroupInfo info = GROUPS.get(group);
    return info != null ? info.title() : group.replace('_', ' ');
  }

  /** The group-level instruction to render once per section, or {@code null} if none applies. */
  public static String instruction(final String group) {
    final GroupInfo info = GROUPS.get(group);
    return info != null ? info.instruction() : null;
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

  private record GroupInfo(String title, String instruction) {
    private GroupInfo(final String title) {
      this(title, null);
    }
  }
}
