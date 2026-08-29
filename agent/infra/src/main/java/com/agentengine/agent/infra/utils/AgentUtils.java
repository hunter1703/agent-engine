package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.Notebook;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.Permission;
import com.google.adk.agents.InvocationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AgentUtils {
  private AgentUtils() {}

  public static ContextStrategyConfig resolveContextStrategy(final BaseAgentConfig agentConfig) {
    if (agentConfig == null || agentConfig.getContextStrategy() == null) {
      return new CompactionContextStrategyConfig();
    }
    return agentConfig.getContextStrategy();
  }

  static String getAgentIdFromContext(final InvocationContext context) {
    if (context == null) {
      return "unknown";
    }
    if (context.agent() != null && StringUtils.isNotBlank(context.agent().name())) {
      return context.agent().name();
    }
    if (context.session() != null && StringUtils.isNotBlank(context.session().appName())) {
      return context.session().appName();
    }
    return "unknown";
  }

  /**
   * Builds a {@link ResourceGrants} from the given entries; the caller is responsible for
   * validating them via {@link #validate} first.
   */
  public static ResourceGrants buildResourceGrants(
      final List<String> knowledgeIds,
      final List<String> knowledgeSources,
      final List<NotebookGrants.Entry> notebookGrantEntries) {
    final NotebookGrants notebookGrants = new NotebookGrants(notebookGrantEntries);
    if (CollectionUtils.isEmpty(knowledgeIds)
        && CollectionUtils.isEmpty(knowledgeSources)
        && notebookGrants.grants().isEmpty()) {
      return null;
    }
    return new ResourceGrants(knowledgeIds, knowledgeSources, notebookGrants);
  }

  /**
   * The one place every rule about what makes a grant entry valid lives: its permission must be a
   * real value, note_title must be present iff the permission needs one (read_note/edit_note do,
   * create_note doesn't), and — since a permission always targets an asset that already exists —
   * that asset must actually exist: create_note targets the notebook itself, read_note/edit_note
   * each target one specific note. Returns every violation found rather than stopping at the first,
   * so the caller can report them all at once instead of just the first.
   *
   * <p>Existence is checked with one batched {@code findByIds} call per repository, covering every
   * entry that passed the format checks, rather than one {@code findById} call per entry.
   */
  public static List<Violation> validate(
      final List<NotebookGrants.Entry> entries,
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository) {
    final List<Violation> violations = new ArrayList<>();
    final List<NotebookGrants.Entry> notebookScoped = new ArrayList<>();
    final List<NotebookGrants.Entry> noteScoped = new ArrayList<>();

    for (final NotebookGrants.Entry entry : CollectionUtils.nullSafeList(entries)) {
      final Permission permission = NotebookGrants.Entry.parsePermission(entry.getPermission());
      final boolean hasNoteTitle = StringUtils.isNotBlank(entry.getNoteTitle());
      if (permission == Permission.UNKNOWN) {
        final Violation.Builder builder =
            Violation.builder("invalid_permission")
                .message(
                    "Grant for "
                        + (hasNoteTitle
                            ? "note '" + entry.getNoteTitle() + "' in notebook '"
                            : "notebook '")
                        + entry.getNotebookId()
                        + "' has invalid permission: "
                        + entry.getPermission())
                .detail("notebookId", entry.getNotebookId())
                .detail("permission", entry.getPermission());
        if (hasNoteTitle) {
          builder.detail("noteTitle", entry.getNoteTitle());
        }
        violations.add(builder.build());
      } else if (permission == Permission.CREATE && hasNoteTitle) {
        violations.add(
            Violation.builder("create_note_with_note_title")
                .message(
                    "Grant for notebook '"
                        + entry.getNotebookId()
                        + "' assigns create_note with note_title set — create_note applies to "
                        + "the whole notebook, omit note_title.")
                .detail("notebookId", entry.getNotebookId())
                .detail("noteTitle", entry.getNoteTitle())
                .build());
      } else if (permission != Permission.CREATE && !hasNoteTitle) {
        violations.add(
            Violation.builder("missing_note_title")
                .message(
                    "Grant for notebook '"
                        + entry.getNotebookId()
                        + "' assigns "
                        + entry.getPermission()
                        + " without a note_title — read_note/edit_note require naming the note.")
                .detail("notebookId", entry.getNotebookId())
                .build());
      } else if (hasNoteTitle) {
        noteScoped.add(entry);
      } else {
        notebookScoped.add(entry);
      }
    }

    final Map<String, Notebook> existingNotebooks =
        notebookScoped.isEmpty()
            ? Map.of()
            : notebookRepository.findByIds(
                notebookScoped.stream().map(NotebookGrants.Entry::getNotebookId).toList());
    for (final NotebookGrants.Entry entry : notebookScoped) {
      if (!existingNotebooks.containsKey(entry.getNotebookId())) {
        violations.add(
            Violation.builder("notebook_not_found")
                .message(
                    "Grant for notebook '"
                        + entry.getNotebookId()
                        + "' targets a notebook that doesn't exist.")
                .detail("notebookId", entry.getNotebookId())
                .build());
      }
    }

    final Map<String, Note> existingNotes =
        noteScoped.isEmpty()
            ? Map.of()
            : notesRepository.findByIds(
                noteScoped.stream()
                    .map(entry -> NotebookUtils.noteId(entry.getNotebookId(), entry.getNoteTitle()))
                    .toList());
    for (final NotebookGrants.Entry entry : noteScoped) {
      final String noteId = NotebookUtils.noteId(entry.getNotebookId(), entry.getNoteTitle());
      if (!existingNotes.containsKey(noteId)) {
        violations.add(
            Violation.builder("note_not_found")
                .message(
                    "Grant for note '"
                        + entry.getNoteTitle()
                        + "' in notebook '"
                        + entry.getNotebookId()
                        + "' uses read_note/edit_note, but that note doesn't exist yet — those "
                        + "require an existing note; use create_note instead if the grantee is "
                        + "meant to create it.")
                .detail("notebookId", entry.getNotebookId())
                .detail("noteTitle", entry.getNoteTitle())
                .detail("noteId", noteId)
                .build());
      }
    }

    return violations;
  }
}
