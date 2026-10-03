package com.agentengine.agent.infra.notebook;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.context.Principal;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything done with notebooks and notes, in the current context: the only code that reads or
 * writes them. Also tells an agent which notebooks and notes it can reach, read on each call from
 * the notebooks the current context may read.
 */
@Singleton
public class NotebookService {

  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;

  @Inject
  public NotebookService(
      final NotebookRepository notebookRepository, final NotesRepository notesRepository) {
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
  }

  /**
   * @throws DuplicateAssetException when a notebook already has the id
   */
  public Notebook createNotebook(final Notebook notebook) {
    return notebookRepository.insert(notebook);
  }

  /** Deletes the notebook and its notes; false when there is none the caller may delete. */
  public boolean deleteNotebook(final String notebookId) {
    if (!notebookRepository.hasPermission(notebookId, Permission.DELETE)) {
      return false;
    }
    notesRepository.deleteByFilterIgnoringVersion(Filters.eq(Note.FIELD_NOTEBOOK_ID, notebookId));
    return notebookRepository.deleteByIdIgnoringVersion(notebookId);
  }

  /** The note, when it exists and the caller may read its notebook; null otherwise. */
  public Note getNote(final String notebookId, final String noteTitle) {
    return notebookRepository.hasPermission(notebookId, Permission.READ)
        ? notesRepository.findById(NotebookUtils.noteId(notebookId, noteTitle))
        : null;
  }

  /** Whether the caller may add, change and delete notes in the notebook. */
  public boolean canWriteNotes(final String notebookId) {
    return notebookRepository.hasPermission(notebookId, Permission.EDIT);
  }

  /**
   * Writes the note's content, creating the note or replacing an existing one's.
   *
   * @throws UnauthorizedException when the caller may not write notes in the notebook
   */
  public Note saveNote(final String notebookId, final String noteTitle, final String content) {
    if (!canWriteNotes(notebookId)) {
      throw new UnauthorizedException(AssetClass.NOTEBOOK, notebookId);
    }
    // A note is set to the content given, whatever was written to it before.
    return notesRepository.saveIgnoringVersion(new Note(notebookId, noteTitle, content));
  }

  /**
   * Deletes the note; false when there is none, or the caller may not write notes in its notebook.
   */
  public boolean deleteNote(final String notebookId, final String noteTitle) {
    return canWriteNotes(notebookId)
        && notesRepository.deleteByIdIgnoringVersion(NotebookUtils.noteId(notebookId, noteTitle));
  }

  /** What prevents granting {@code notebookIds}: notebooks that don't exist. */
  public List<Violation> grantViolations(final List<String> notebookIds) {
    if (CollectionUtils.isEmpty(notebookIds)) {
      return List.of();
    }
    final Map<String, Notebook> existing = notebookRepository.findByIds(notebookIds);
    final List<Violation> violations = new ArrayList<>();
    for (final String notebookId : notebookIds) {
      if (!existing.containsKey(notebookId)) {
        violations.add(
            Violation.builder("notebook_not_found")
                .message(
                    "Grant for notebook '"
                        + notebookId
                        + "' targets a notebook that doesn't exist.")
                .detail("notebookId", notebookId)
                .build());
      }
    }
    return violations;
  }

  /**
   * The sharing that gives {@code session}, a session bound to no user, the notebooks as an editor:
   * it may read, add, change and delete their notes. Notebooks are made by agents within sessions,
   * so they are always handed on to a session as a whole.
   */
  public List<SharingChange> sharingChanges(
      final Principal session, final List<String> notebookIds) {
    final List<SharingChange> changes = new ArrayList<>();
    for (final String notebookId : CollectionUtils.nullSafeList(notebookIds)) {
      changes.add(
          SharingChange.ofAsset(
              AssetClass.NOTEBOOK,
              notebookId,
              Map.of(session.toString(), Set.of(StandardRole.EDITOR))));
    }
    return changes;
  }

  /** Each notebook the caller can reach, whether it may write notes in it, and its notes. */
  public String summary() {
    final Map<String, NotebookSummary> notebookIdVsSummary = new LinkedHashMap<>();
    for (final Notebook notebook :
        notebookRepository.findByQuery(new Query().withPage(Page.UNBOUNDED)).getItems()) {
      notebookIdVsSummary.put(
          notebook.getId(),
          new NotebookSummary(notebookRepository.hasPermission(notebook, Permission.EDIT)));
    }
    if (notebookIdVsSummary.isEmpty()) {
      return "You have no notebook access.";
    }
    for (final Note note :
        notesRepository
            .findByQuery(
                new Query()
                    .withFilter(
                        Filters.in(
                            Note.FIELD_NOTEBOOK_ID, List.copyOf(notebookIdVsSummary.keySet())))
                    .withExcludeFields(List.of(Note.FIELD_CONTENT))
                    .withPage(Page.UNBOUNDED))
            .getItems()) {
      final NotebookSummary summary = notebookIdVsSummary.get(note.getNotebookId());
      if (summary != null) {
        summary.noteTitles.add(note.getNoteTitle());
      }
    }
    final StringBuilder sb = new StringBuilder();
    int index = 1;
    for (final Map.Entry<String, NotebookSummary> entry : notebookIdVsSummary.entrySet()) {
      final NotebookSummary summary = entry.getValue();
      sb.append("   ").append(index++).append(". Notebook id: `");
      sb.append(entry.getKey()).append("`\n");
      sb.append("      - add, change and delete notes: ")
          .append(summary.canWrite ? "yes" : "no, read only")
          .append("\n");
      sb.append("      - notes:");
      if (summary.noteTitles.isEmpty()) {
        sb.append(" (none)\n");
      } else {
        sb.append("\n");
        for (final String noteTitle : summary.noteTitles) {
          sb.append("          - `").append(noteTitle).append("`\n");
        }
      }
    }
    return sb.toString().trim();
  }

  private static final class NotebookSummary {
    private final boolean canWrite;
    private final Set<String> noteTitles = new LinkedHashSet<>();

    private NotebookSummary(final boolean canWrite) {
      this.canWrite = canWrite;
    }
  }
}
