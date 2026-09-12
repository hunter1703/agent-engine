package com.agentengine.agent.api.model;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.Permission;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record NotebookGrants(Map<String, Permission> grants) {

  public NotebookGrants(Map<String, Permission> grants) {
    this.grants = CollectionUtils.nullSafeMap(grants);
  }

  public NotebookGrants(
      final List<NotebookGrant> notebookGrants, final List<NoteGrant> noteGrants) {
    this(resolve(notebookGrants, noteGrants));
  }

  public static String noteGrantKey(final String notebookId, final String noteTitle) {
    return NotebookUtils.noteId(notebookId, noteTitle);
  }

  public boolean canRead(final String notebookId, final String noteTitle) {
    final Permission permission = grants.get(noteGrantKey(notebookId, noteTitle));
    return permission == Permission.READ || permission == Permission.WRITE;
  }

  public boolean canWrite(final String notebookId, final String noteTitle) {
    return grants.get(noteGrantKey(notebookId, noteTitle)) == Permission.WRITE;
  }

  public boolean canCreate(final String notebookId) {
    return grants.get(notebookId) == Permission.CREATE;
  }

  /**
   * If {@code attemptedNotebookId} doesn't match any grant but is exactly the trailing segment of
   * one granted notebook's full id (e.g. a caller passed the human-friendly short name it was
   * given, like {@code "valiantkingsaga"}, instead of the full {@code
   * "story_agent:abc:valiantkingsaga"} it was actually granted), returns that full id so an
   * access-denied error can hand the model the exact corrected string to use next — cheaper for a
   * weaker model to copy verbatim than to derive by parsing a grants list. Returns {@code null} if
   * there's no such unambiguous match (including when {@code attemptedNotebookId} already matches a
   * grant, since then the mismatch is something other than a shortened name).
   */
  public String suggestNotebookId(final String attemptedNotebookId) {
    if (StringUtils.isBlank(attemptedNotebookId) || grants.containsKey(attemptedNotebookId)) {
      return null;
    }
    final String suffix = ":" + attemptedNotebookId;
    String match = null;
    for (final String key : grants.keySet()) {
      final String notebookId = NotebookUtils.isNoteId(key) ? NotebookUtils.notebookIdOf(key) : key;
      if (notebookId != null && notebookId.endsWith(suffix)) {
        if (match != null && !match.equals(notebookId)) {
          return null; // ambiguous — more than one granted notebook ends with this short name
        }
        match = notebookId;
      }
    }
    return match;
  }

  /**
   * Renders every grant as a human/LLM-readable, per-notebook bullet list — the exact notebook and
   * note ids to use, grouped by notebook. Shared by {@code ReminderPlugin}'s proactive brief and by
   * notebook tools' access-denied errors, so a caller that used a wrong id (e.g. dropping a
   * notebook's name suffix) sees its actual grants alongside the error and can self-correct.
   */
  public String describe() {
    if (grants.isEmpty()) {
      return "You have no notebook access.";
    }
    final Map<String, NotebookSummary> summaries = new LinkedHashMap<>();
    for (final Map.Entry<String, Permission> entry : grants.entrySet()) {
      final String key = entry.getKey();
      final Permission permission = entry.getValue();
      if (NotebookUtils.isNoteId(key)) {
        final String notebookId = NotebookUtils.notebookIdOf(key);
        final String noteTitle = NotebookUtils.noteTitleOf(key);
        final NotebookSummary summary =
            summaries.computeIfAbsent(notebookId, k -> new NotebookSummary());
        if (permission == Permission.WRITE) {
          summary.editPermissionedNotes.add(noteTitle);
        } else if (permission == Permission.READ) {
          summary.readPermissionedNotes.add(noteTitle);
        }
      } else if (permission == Permission.CREATE) {
        summaries.computeIfAbsent(key, k -> new NotebookSummary()).canCreate = true;
      }
    }

    final StringBuilder sb = new StringBuilder();
    for (final Map.Entry<String, NotebookSummary> entry : summaries.entrySet()) {
      final String notebookId = entry.getKey();
      final NotebookSummary summary = entry.getValue();
      sb.append("- Notebook '").append(notebookId).append("': ");
      sb.append(
          summary.canCreate
              ? "you have notebook-wide access (may add a note under any title that doesn't "
                  + "exist there yet)."
              : "you do not have notebook-wide access.");
      if (!summary.editPermissionedNotes.isEmpty()) {
        sb.append("\n  - edit access: ").append(String.join(", ", summary.editPermissionedNotes));
      }
      if (!summary.readPermissionedNotes.isEmpty()) {
        sb.append("\n  - read access: ").append(String.join(", ", summary.readPermissionedNotes));
      }
      sb.append("\n");
    }
    return sb.toString().trim();
  }

  private static final class NotebookSummary {
    private boolean canCreate;
    private final Set<String> readPermissionedNotes = new LinkedHashSet<>();
    private final Set<String> editPermissionedNotes = new LinkedHashSet<>();
  }

  private static Map<String, Permission> resolve(
      final List<NotebookGrant> notebookGrants, final List<NoteGrant> noteGrants) {
    final Map<String, Permission> resolved = new HashMap<>();
    for (final NotebookGrant grant : CollectionUtils.nullSafeList(notebookGrants)) {
      resolved.put(grant.getNotebookId(), Permission.CREATE);
    }
    for (final NoteGrant grant : CollectionUtils.nullSafeList(noteGrants)) {
      resolved.put(
          noteGrantKey(grant.getNotebookId(), grant.getNoteTitle()),
          NoteGrant.parsePermission(grant.getPermission()));
    }
    return resolved;
  }

  /**
   * Grants notebook-wide access to an entire notebook — the ability to add new notes anywhere in it
   * — so there is deliberately no note title field here to be set inconsistently.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class NotebookGrant {

    @JsonProperty("notebook_id")
    @ToolSchema(
        description =
            """
            The notebook to grant notebook-wide access to — lets the grantee add a new note anywhere in it.

            Must already exist — granting access to a notebook that doesn't exist yet will fail.
            """)
    private String notebookId;

    public NotebookGrant() {}

    public String getNotebookId() {
      return notebookId;
    }

    public void setNotebookId(final String notebookId) {
      this.notebookId = notebookId;
    }
  }

  /**
   * Grants read or edit access to one specific, already-existing note. Both fields are required,
   * and notebook-wide access is deliberately excluded from the permission enum — grant that via
   * {@link NotebookGrant} instead.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class NoteGrant {

    @JsonProperty("notebook_id")
    @ToolSchema(
        description =
            """
            The notebook containing the note.

            Must already exist — granting access to a notebook that doesn't exist yet will fail.
            """)
    private String notebookId;

    @JsonProperty("note_title")
    @ToolSchema(
        description =
            """
            The note within the notebook to grant access to.

            Must already exist — granting access to a note that doesn't exist yet will fail. To let the grantee create a brand-new note instead, grant it notebook-wide access instead of using this.
            """)
    private String noteTitle;

    @ToolSchema(
        description =
            """
            What access to grant to this specific note: read-only, or read and edit (the ability to overwrite or delete the note — this also includes read access, so there's no need to grant both separately for the same note).
            """,
        enums = {"read_note", "edit_note"})
    private String permission;

    public NoteGrant() {}

    public static Permission parsePermission(final String value) {
      if (value == null) {
        return Permission.UNKNOWN;
      }
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "read_note" -> Permission.READ;
        case "edit_note" -> Permission.WRITE;
        default -> Permission.UNKNOWN;
      };
    }

    public String getNotebookId() {
      return notebookId;
    }

    public void setNotebookId(final String notebookId) {
      this.notebookId = notebookId;
    }

    public String getNoteTitle() {
      return noteTitle;
    }

    public void setNoteTitle(final String noteTitle) {
      this.noteTitle = noteTitle;
    }

    public String getPermission() {
      return permission;
    }

    public void setPermission(final String permission) {
      this.permission = permission;
    }
  }
}
