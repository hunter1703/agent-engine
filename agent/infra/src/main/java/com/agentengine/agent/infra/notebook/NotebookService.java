package com.agentengine.agent.infra.notebook;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tenancy.Grant;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Everything done with notebooks and notes, in the current context: the only code that reads or
 * writes them. Also tells an agent which notebooks and notes it can reach, read on each call from
 * the notebooks the current context may read.
 */
@Singleton
public class NotebookService {

  private static final String SUMMARY_CACHE = "NOTEBOOK_SUMMARY";
  private static final String SCRATCHPAD_ID = "scratchpad";
  private static final String SCRATCHPAD_NAME = "Scratchpad";
  private static final String SCRATCHPAD_DESCRIPTION =
      "Your own working notes for this task: intermediate findings, calculations and drafts you"
          + " will need again later. Not for the deliverable itself, or for anything another"
          + " agent or a later session needs to find.";
  private static final Notebook SCRATCHPAD_LISTING = scratchpadListing();
  private static final int MAX_APPEND_ATTEMPTS = 3;

  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;
  private final DistributedCache<Map<String, String>> summaryCache;
  private final LoadingCache<String, Map<String, Note>> scratchpadNotes;

  @Inject
  public NotebookService(
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository,
      final DistributedCacheManager cacheManager) {
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
    this.summaryCache =
        new DistributedCache.Builder<Map<String, String>>(SUMMARY_CACHE, cacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES))
            .loader(this::loadSummaries)
            .build();
    this.scratchpadNotes =
        CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(CacheLoader.from(this::loadScratchpadNotes));
  }

  /**
   * @throws DuplicateAssetException when a notebook already has the id
   */
  public Notebook createNotebook(final Notebook notebook) {
    final Notebook created = notebookRepository.insert(notebook);
    invalidateNotebookCache(created);
    return created;
  }

  /**
   * Makes sure session {@code sessionId} has its scratchpad: a notebook like any other, named after
   * the session so there is one per session.
   */
  public void ensureScratchpad(final String sessionId) {
    final String scratchpadId = NotebookUtils.notebookId(sessionId, SCRATCHPAD_NAME);
    if (scratchpadNotes.getIfPresent(scratchpadId) != null
        || notebookRepository.findById(scratchpadId) != null) {
      return;
    }
    final Notebook scratchpad = new Notebook(sessionId, SCRATCHPAD_NAME, SCRATCHPAD_DESCRIPTION);
    scratchpad.setId(scratchpadId);
    try {
      createNotebook(scratchpad);
    } catch (final DuplicateAssetException alreadyCreated) {
      // Created meanwhile by another agent of the session.
    }
  }

  /** Deletes the notebooks session {@code sessionId} made, scratchpad included, with their notes. */
  public void deleteSessionNotebooks(final String sessionId) {
    final List<String> notebookIds =
        notebookRepository
            .findByQuery(
                new Query()
                    .withFilter(Filters.eq(Notebook.FIELD_AUTHOR, sessionId))
                    .withPage(Page.UNBOUNDED))
            .getItems()
            .stream()
            .map(Notebook::getId)
            .toList();
    if (notebookIds.isEmpty()) {
      return;
    }
    notesRepository.deleteByFilterIgnoringVersion(Filters.in(Note.FIELD_NOTEBOOK_ID, notebookIds));
    notebookRepository.deleteByFilterIgnoringVersion(Filters.in(BaseEntity.FIELD_ID, notebookIds));
    notebookIds.forEach(scratchpadNotes::invalidate);
    invalidateCurrentSessionCache();
  }

  public boolean deleteNotebook(final String logicalNotebookId) {
    final String notebookId = physicalNotebookId(logicalNotebookId);
    final Notebook notebook = notebookRepository.findById(notebookId);
    if (notebook == null
        || !notebookRepository.hasPermission(notebook, Permission.DELETE)) {
      return false;
    }
    final boolean deleted = notebookRepository.deleteByIdIgnoringVersion(notebookId);
    if (deleted) {
      notesRepository.deleteByFilterIgnoringVersion(Filters.eq(Note.FIELD_NOTEBOOK_ID, notebookId));
      invalidateNotebookCache(notebook);
      scratchpadNotes.invalidate(notebookId);
    }
    return deleted;
  }

  /** The note, when it exists and the caller may read its notebook; null otherwise. */
  public Note getNote(final String logicalNotebookId, final String noteTitle) {
    final String notebookId = physicalNotebookId(logicalNotebookId);
    final String noteId = NotebookUtils.noteId(notebookId, noteTitle);
    if (isScratchpad(logicalNotebookId)) {
      return scratchpadNotes.getUnchecked(notebookId).get(noteId);
    }
    return notebookRepository.hasPermission(notebookId, Permission.READ)
        ? notesRepository.findById(noteId)
        : null;
  }

  /** Whether the caller may add, change and delete notes in the notebook. */
  public boolean canWriteNotes(final String logicalNotebookId) {
    final String notebookId = physicalNotebookId(logicalNotebookId);
    return isScratchpad(logicalNotebookId) || notebookRepository.hasPermission(notebookId, Permission.EDIT);
  }

  /**
   * Writes the note's content, creating the note or replacing an existing one's, provided the note
   * is still at {@code expectedVersion} (null when it was not there).
   *
   * @throws UnauthorizedException when the caller may not write notes in the notebook
   * @throws StaleStateException when the note changed since that version
   * @throws DuplicateAssetException when the note was created since
   */
  public Note saveNote(
      final String logicalNotebookId,
      final String noteTitle,
      final String content,
      final Long expectedVersion) {
    final String notebookId = physicalNotebookId(logicalNotebookId);
    final Note replacement = new Note(notebookId, noteTitle, content);
    replacement.setVersion(expectedVersion);
    if (isScratchpad(logicalNotebookId)) {
      return cacheScratchpadNote(notebookId, notesRepository.save(replacement));
    }
    final Notebook notebook = notebookRepository.findById(notebookId);
    if (notebook == null || !notebookRepository.hasPermission(notebook, Permission.EDIT)) {
      throw new UnauthorizedException(AssetClass.NOTEBOOK, notebookId);
    }
    final Note saved = notesRepository.save(replacement);
    invalidateNotebookCache(notebook);
    return saved;
  }

  /**
   * Appends content to a note, creating the note if it does not exist yet. An append that races
   * with another write to the note is read and applied again.
   *
   * @throws UnauthorizedException when the caller may not write notes in the notebook
   */
  public Note appendNote(
      final String logicalNotebookId, final String noteTitle, final String contentToAppend) {
    final String notebookId = physicalNotebookId(logicalNotebookId);
    final boolean scratchpad = isScratchpad(logicalNotebookId);
    final Notebook notebook = scratchpad ? null : notebookRepository.findById(notebookId);
    if (!scratchpad
        && (notebook == null || !notebookRepository.hasPermission(notebook, Permission.EDIT))) {
      throw new UnauthorizedException(AssetClass.NOTEBOOK, notebookId);
    }
    final String noteId = NotebookUtils.noteId(notebookId, noteTitle);
    for (int attempt = 1; ; attempt++) {
      try {
        final Note existing =
            scratchpad
                ? scratchpadNotes.getUnchecked(notebookId).get(noteId)
                : notesRepository.findById(noteId);
        final Note appended =
            existing == null ? new Note(notebookId, noteTitle, contentToAppend) : existing;
        if (existing != null) {
          appended.setContent(
              StringUtils.isBlank(existing.getContent())
                  ? contentToAppend
                  : existing.getContent() + "\n\n" + contentToAppend);
        }
        final Note saved = notesRepository.save(appended);
        if (scratchpad) {
          return cacheScratchpadNote(notebookId, saved);
        }
        invalidateNotebookCache(notebook);
        return saved;
      } catch (final StaleStateException | DuplicateAssetException raced) {
        if (scratchpad) {
          scratchpadNotes.invalidate(notebookId);
        }
        if (attempt == MAX_APPEND_ATTEMPTS) {
          throw raced;
        }
      }
    }
  }

  /**
   * Deletes the note; false when there is none, or the caller may not write notes in its notebook.
   */
  public boolean deleteNote(final String logicalNotebookId, final String noteTitle) {
    final String notebookId = physicalNotebookId(logicalNotebookId);
    final String noteId = NotebookUtils.noteId(notebookId, noteTitle);
    if (isScratchpad(logicalNotebookId)) {
      final boolean deleted = notesRepository.deleteByIdIgnoringVersion(noteId);
      if (deleted) {
        scratchpadNotes.getUnchecked(notebookId).remove(noteId);
      }
      return deleted;
    }
    final Notebook notebook = notebookRepository.findById(notebookId);
    if (notebook == null || !notebookRepository.hasPermission(notebook, Permission.EDIT)) {
      return false;
    }
    final boolean deleted = notesRepository.deleteByIdIgnoringVersion(noteId);
    if (deleted) {
      invalidateNotebookCache(notebook);
    }
    return deleted;
  }

  /**
   * Whether the notebook is the current session's scratchpad: a notebook only that session writes,
   * so its notes are read from a local copy that every write to it also updates.
   */
  public boolean isScratchpad(final String logicalNotebookId) {
    return SCRATCHPAD_ID.equalsIgnoreCase(logicalNotebookId.trim());
  }

  /** The current session's scratchpad as listed in the notebook summary; empty outside a session. */
  public String scratchpadSummary() {
    final String sessionId = currentSessionId();
    if (sessionId == null) {
      return "";
    }
    return SCRATCHPAD_LISTING.summarize(
        true,
        scratchpadNotes
            .getUnchecked(NotebookUtils.notebookId(sessionId, SCRATCHPAD_NAME))
            .values()
            .stream()
            .map(Note::getNoteTitle)
            .toList());
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
              Map.of(session.toString(), Set.of(StandardRole.OWNER))));
    }
    return changes;
  }

  private Map<String, Note> loadScratchpadNotes(final String scratchpadId) {
    final Map<String, Note> noteIdVsNote = new ConcurrentHashMap<>();
    for (final Note note :
        notesRepository
            .findByQuery(
                new Query()
                    .withFilter(Filters.eq(Note.FIELD_NOTEBOOK_ID, scratchpadId))
                    .withPage(Page.UNBOUNDED))
            .getItems()) {
      noteIdVsNote.put(note.getId(), note);
    }
    return noteIdVsNote;
  }

  private Note cacheScratchpadNote(final String scratchpadId, final Note note) {
    scratchpadNotes.getUnchecked(scratchpadId).put(note.getId(), note);
    return note;
  }

  private void invalidateNotebookCache(final Notebook notebook) {
    invalidateCurrentSessionCache();
    if (notebook == null
        || notebook.getAcl() == null
        || CollectionUtils.isEmpty(notebook.getAcl().grants())) {
      return;
    }
    final Set<String> sessionIds = new LinkedHashSet<>();
    for (final String grantStr : notebook.getAcl().grants()) {
      final Grant grant = Grant.parse(grantStr);
      grant.principal().assetId(AssetClass.AGENT_SESSION).ifPresent(sessionIds::add);
    }
    sessionIds.forEach(summaryCache::invalidate);
  }

  /** The id the notebook is stored under: the scratchpad is stored under its session's id. */
  private String physicalNotebookId(final String logicalNotebookId) {
    return isScratchpad(logicalNotebookId)
        ? NotebookUtils.notebookId(currentSessionId(), SCRATCHPAD_NAME)
        : logicalNotebookId;
  }

  private static Notebook scratchpadListing() {
    final Notebook listing = new Notebook(null, SCRATCHPAD_NAME, SCRATCHPAD_DESCRIPTION);
    listing.setId(SCRATCHPAD_ID);
    return listing;
  }

  private static String currentSessionId() {
    return Context.require()
        .principal()
        .flatMap(principal -> principal.assetId(AssetClass.AGENT_SESSION))
        .orElse(null);
  }

  private void invalidateCurrentSessionCache() {
    final Context context = Context.require();
    context
        .userCaller()
        .ifPresent(
            userCaller -> {
              for (Principal principal : userCaller.principals()) {
                principal.assetId(AssetClass.AGENT_SESSION).ifPresent(summaryCache::invalidate);
              }
            });
  }

  /**
   * The summary of each notebook the caller can reach in the current session, by notebook id,
   * leaving out the session's scratchpad.
   */
  public Map<String, String> summaries(boolean skipCache) {
    final String sessionId = currentSessionId();
    if (sessionId == null) {
      return Map.of();
    }

    if (skipCache) {
      summaryCache.remove(sessionId);
    }
    return summaryCache.get(sessionId);
  }

  private Map<String, String> loadSummaries(final String sessionId) {
    final List<Notebook> notebooks =
        new ArrayList<>(
            notebookRepository.findByQuery(new Query().withPage(Page.UNBOUNDED)).getItems());
    notebooks.sort(
        (a, b) ->
            String.CASE_INSENSITIVE_ORDER.compare(
                a.getName() == null ? "" : a.getName(), b.getName() == null ? "" : b.getName()));

    final Map<String, NotebookSummary> notebookIdVsSummary = new LinkedHashMap<>();
    final String scratchpadId = NotebookUtils.notebookId(sessionId, SCRATCHPAD_NAME);
    for (final Notebook notebook : notebooks) {
      if (scratchpadId.equals(notebook.getId())) {
        continue;
      }
      notebookIdVsSummary.put(
          notebook.getId(),
          new NotebookSummary(
              notebook, notebookRepository.hasPermission(notebook, Permission.EDIT)));
    }
    if (notebookIdVsSummary.isEmpty()) {
      return Map.of();
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
    final Map<String, String> notebookIdVsText = new LinkedHashMap<>();
    notebookIdVsSummary.forEach(
        (notebookId, summary) ->
            notebookIdVsText.put(
                notebookId, summary.notebook.summarize(summary.canWrite, summary.noteTitles)));
    return notebookIdVsText;
  }

  private static final class NotebookSummary {
    private final Notebook notebook;
    private final boolean canWrite;
    private final Set<String> noteTitles = new LinkedHashSet<>();

    private NotebookSummary(final Notebook notebook, final boolean canWrite) {
      this.notebook = notebook;
      this.canWrite = canWrite;
    }
  }
}
