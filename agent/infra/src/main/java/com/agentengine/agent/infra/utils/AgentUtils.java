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
   * Builds a {@link ResourceGrants} from the given grants; the caller is responsible for validating
   * them via {@link #validate} first.
   */
  public static ResourceGrants buildResourceGrants(
      final List<String> knowledgeIds,
      final List<String> knowledgeSources,
      final List<NotebookGrants.NotebookGrant> notebookGrants,
      final List<NotebookGrants.NoteGrant> noteGrants) {
    final NotebookGrants resolvedGrants = new NotebookGrants(notebookGrants, noteGrants);
    if (CollectionUtils.isEmpty(knowledgeIds)
        && CollectionUtils.isEmpty(knowledgeSources)
        && resolvedGrants.grants().isEmpty()) {
      return null;
    }
    return new ResourceGrants(knowledgeIds, knowledgeSources, resolvedGrants);
  }

  /**
   * The one place every rule about what makes a set of grants valid lives. {@link
   * NotebookGrants.NotebookGrant}/{@link NotebookGrants.NoteGrant} being separately-typed already
   * rules out the permission/note_title mismatch a single combined shape used to allow — what's
   * left to check is a genuinely invalid permission string on a note grant, and that every grant
   * targets an asset that actually exists: a notebook grant targets the notebook itself, a note
   * grant targets one specific, already-existing note. Returns every violation found rather than
   * stopping at the first, so the caller can report them all at once.
   *
   * <p>Existence is checked with one batched {@code findByIds} call per repository, covering every
   * grant that passed the format checks, rather than one {@code findById} call per grant.
   */
  public static List<Violation> validate(
      final List<NotebookGrants.NotebookGrant> notebookGrants,
      final List<NotebookGrants.NoteGrant> noteGrants,
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository) {
    final List<Violation> violations = new ArrayList<>();

    final List<NotebookGrants.NotebookGrant> notebookScoped =
        CollectionUtils.nullSafeList(notebookGrants);
    final Map<String, Notebook> existingNotebooks =
        notebookScoped.isEmpty()
            ? Map.of()
            : notebookRepository.findByIds(
                notebookScoped.stream().map(NotebookGrants.NotebookGrant::getNotebookId).toList());
    for (final NotebookGrants.NotebookGrant grant : notebookScoped) {
      if (!existingNotebooks.containsKey(grant.getNotebookId())) {
        violations.add(
            Violation.builder("notebook_not_found")
                .message(
                    "Grant for notebook '"
                        + grant.getNotebookId()
                        + "' targets a notebook that doesn't exist.")
                .detail("notebookId", grant.getNotebookId())
                .build());
      }
    }

    final List<NotebookGrants.NoteGrant> noteScoped = new ArrayList<>();
    for (final NotebookGrants.NoteGrant grant : CollectionUtils.nullSafeList(noteGrants)) {
      final Permission permission = NotebookGrants.NoteGrant.parsePermission(grant.getPermission());
      if (permission == Permission.UNKNOWN) {
        violations.add(
            Violation.builder("invalid_permission")
                .message(
                    "Grant for note '"
                        + grant.getNoteTitle()
                        + "' in notebook '"
                        + grant.getNotebookId()
                        + "' has invalid permission: "
                        + grant.getPermission())
                .detail("notebookId", grant.getNotebookId())
                .detail("noteTitle", grant.getNoteTitle())
                .detail("permission", grant.getPermission())
                .build());
      } else {
        noteScoped.add(grant);
      }
    }

    final Map<String, Note> existingNotes =
        noteScoped.isEmpty()
            ? Map.of()
            : notesRepository.findByIds(
                noteScoped.stream()
                    .map(grant -> NotebookUtils.noteId(grant.getNotebookId(), grant.getNoteTitle()))
                    .toList());
    for (final NotebookGrants.NoteGrant grant : noteScoped) {
      final String noteId = NotebookUtils.noteId(grant.getNotebookId(), grant.getNoteTitle());
      if (!existingNotes.containsKey(noteId)) {
        violations.add(
            Violation.builder("note_not_found")
                .message(
                    "Grant for note '"
                        + grant.getNoteTitle()
                        + "' in notebook '"
                        + grant.getNotebookId()
                        + "' grants read or edit access, but that note doesn't exist yet — those "
                        + "require an existing note; grant notebook-wide access instead if "
                        + "the grantee is meant to create it.")
                .detail("notebookId", grant.getNotebookId())
                .detail("noteTitle", grant.getNoteTitle())
                .detail("noteId", noteId)
                .build());
      }
    }

    return violations;
  }
}
