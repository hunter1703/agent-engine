package com.agentengine.agent.api.model;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.Permission;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.List;
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
      final Permission permission = Permission.valueOfOrDefault(entry.getPermission());
      if (permission == Permission.UNKNOWN) {
        throw new IllegalArgumentException(
            "Grant for notebook '"
                + entry.getNotebookId()
                + "' has invalid permission: "
                + entry.getPermission());
      }
      final boolean hasNoteTitle = StringUtils.isNotBlank(entry.getNoteTitle());
      if (permission == Permission.CREATE && hasNoteTitle) {
        throw new IllegalArgumentException(
            "Grant for notebook '"
                + entry.getNotebookId()
                + "' assigns CREATE with note_title set — CREATE applies to the whole "
                + "notebook, omit note_title.");
      }
      if (permission != Permission.CREATE && !hasNoteTitle) {
        throw new IllegalArgumentException(
            "Grant for notebook '"
                + entry.getNotebookId()
                + "' assigns "
                + permission
                + " without a note_title — READ/WRITE require naming the note.");
      }
      final String key =
          hasNoteTitle
              ? noteGrantKey(entry.getNotebookId(), entry.getNoteTitle())
              : entry.getNotebookId();
      resolved.put(key, permission);
    }
    return resolved;
  }

  /** One grant a caller asked to hand a spawned/messaged agent, as given via a tool call. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Entry {

    @ToolSchema(description = "The notebook to grant access to.")
    private String notebookId;

    @ToolSchema(
        description = "Required when permission is READ or WRITE. Omit when permission is CREATE.",
        optional = true)
    private String noteTitle;

    @ToolSchema(
        description =
            "CREATE grants the ability to add new notes anywhere in the notebook. READ/WRITE "
                + "grant access to the one note named by note_title.")
    private String permission;

    public Entry() {}

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
