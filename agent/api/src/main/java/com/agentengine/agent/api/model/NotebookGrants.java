package com.agentengine.agent.api.model;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.Permission;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record NotebookGrants(Map<String, Permission> grants) {

  public NotebookGrants(Map<String, Permission> grants) {
    this.grants = CollectionUtils.nullSafeMap(grants);
  }

  public NotebookGrants(final List<Entry> entries) {
    this(resolve(entries));
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

  private static Map<String, Permission> resolve(final List<Entry> entries) {
    final Map<String, Permission> resolved = new HashMap<>();
    for (final Entry entry : CollectionUtils.nullSafeList(entries)) {
      final Permission permission = Entry.parsePermission(entry.getPermission());
      final String key =
          StringUtils.isNotBlank(entry.getNoteTitle())
              ? noteGrantKey(entry.getNotebookId(), entry.getNoteTitle())
              : entry.getNotebookId();
      resolved.put(key, permission);
    }
    return resolved;
  }

  /** One grant a caller asked to hand a spawned/messaged agent, as given via a tool call. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Entry {

    @JsonProperty("notebook_id")
    @ToolSchema(description = "The notebook to grant access to.")
    private String notebookId;

    @JsonProperty("note_title")
    @ToolSchema(
        description =
            "Required when permission is read_note or edit_note. Omit when permission is "
                + "create_note.",
        optional = true)
    private String noteTitle;

    @ToolSchema(
        description =
            "A permission always targets an existing asset. create_note targets the notebook "
                + "itself (which must already exist) and grants the ability to add a note under a "
                + "title that doesn't exist there yet — omit note_title, since create_note never "
                + "targets a specific note. read_note and edit_note each target one specific, "
                + "already-existing note named by note_title — read_note lets the grantee read it, "
                + "edit_note lets the grantee overwrite or delete it; neither can act on a title "
                + "that doesn't exist yet, that always needs create_note on the notebook instead.",
        enums = {"create_note", "read_note", "edit_note"})
    private String permission;

    public Entry() {}

    public static Permission parsePermission(final String value) {
      if (value == null) {
        return Permission.UNKNOWN;
      }
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "create_note" -> Permission.CREATE;
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
