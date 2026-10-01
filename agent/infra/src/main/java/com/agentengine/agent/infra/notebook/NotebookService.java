package com.agentengine.agent.infra.notebook;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.context.Caller;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Everything done with notebooks and notes, in the current context: the only code that reads or
 * writes them. Also tells an agent which notebooks and notes it can reach — the customer's
 * notebooks and notes, without their content, are cached for the whole customer until one of them
 * changes, and what the current context reaches is worked out from their grants on each call.
 */
@Singleton
public class NotebookService {

  public static final String CACHE_NAME = "NOTEBOOKS";
  private static final String ALL_KEY = "all";
  private static final long CACHE_TTL_MINUTES = 30;

  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;
  private final PermissionChecker permissionChecker;
  private final DistributedCache<Notebooks> cache;

  @Inject
  public NotebookService(
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository,
      final PermissionChecker permissionChecker,
      final DistributedCacheManager cacheManager) {
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
    this.permissionChecker = permissionChecker;
    this.cache =
        new DistributedCache.Builder<Notebooks>(CACHE_NAME, cacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(
                CacheBuilder.newBuilder().expireAfterAccess(CACHE_TTL_MINUTES, TimeUnit.MINUTES))
            .loader(_ -> Context.require().as(Caller.SYSTEM).get(this::loadCustomerNotebooks))
            .build();
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
    final Note note = new Note(notebookId, noteTitle, content);
    final Note existing = notesRepository.findById(note.getId());
    if (existing != null) {
      note.setVersion(existing.getVersion());
    }
    return notesRepository.save(note);
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
    final Notebooks all = cache.get(ALL_KEY);
    final Map<String, NotebookSummary> notebookIdVsSummary = new LinkedHashMap<>();
    for (final Notebook notebook : all.notebooks()) {
      if (hasNotebookPermission(notebook, Permission.READ)) {
        notebookIdVsSummary.put(
            notebook.getId(),
            new NotebookSummary(hasNotebookPermission(notebook, Permission.EDIT)));
      }
    }
    for (final Note note : all.notes()) {
      final NotebookSummary summary = notebookIdVsSummary.get(note.getNotebookId());
      if (summary != null) {
        summary.noteTitles.add(note.getNoteTitle());
      }
    }
    if (notebookIdVsSummary.isEmpty()) {
      return "You have no notebook access.";
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

  /** Drops the customer's cached notebooks, on every node, whenever one of them changes. */
  @Produces
  @Singleton
  public static EntityChangeListener<Notebook> notebookEvictionListener(
      final DistributedCacheManager cacheManager) {
    return new NotebooksEvictionListener<>(Notebook.class, cacheManager);
  }

  /** Drops the customer's cached notebooks, on every node, whenever one of its notes changes. */
  @Produces
  @Singleton
  public static EntityChangeListener<Note> noteEvictionListener(
      final DistributedCacheManager cacheManager) {
    return new NotebooksEvictionListener<>(Note.class, cacheManager);
  }

  private Notebooks loadCustomerNotebooks() {
    return new Notebooks(
        notebookRepository.findByQuery(new Query().withPage(Page.UNBOUNDED)).getItems(),
        notesRepository
            .findByQuery(
                new Query().withPage(Page.UNBOUNDED).withExcludeFields(List.of(Note.FIELD_CONTENT)))
            .getItems());
  }

  private boolean hasNotebookPermission(final Notebook notebook, final Permission permission) {
    return permissionChecker.hasPermission(() -> notebook, AssetClass.NOTEBOOK, permission);
  }

  /** Every notebook of the customer, and every note without its content. */
  private record Notebooks(List<Notebook> notebooks, List<Note> notes) {}

  private record NotebooksEvictionListener<T extends BaseEntity>(
      Class<T> entityClass, DistributedCacheManager cacheManager)
      implements EntityChangeListener<T> {

    @Override
    public void onChange(final EntityChange<T> change) {
      cacheManager.invalidate(CACHE_NAME, ALL_KEY);
    }
  }

  private static final class NotebookSummary {
    private final boolean canWrite;
    private final Set<String> noteTitles = new LinkedHashSet<>();

    private NotebookSummary(final boolean canWrite) {
      this.canWrite = canWrite;
    }
  }
}
