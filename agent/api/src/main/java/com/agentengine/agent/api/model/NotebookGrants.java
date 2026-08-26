package com.agentengine.agent.api.model;

import com.agentengine.util.common.CollectionUtils;
import java.util.Map;

public record NotebookGrants(Map<String, Permission> grants) {

  public enum Permission {
    READ,
    WRITE,
    CREATE,
    UNKNOWN;

    public static Permission valueOfOrUnknown(final String value) {
      try {
        return Permission.valueOf(value);
      } catch (IllegalArgumentException exception) {
        return Permission.UNKNOWN;
      }
    }
  }

  public NotebookGrants(Map<String, Permission> grants) {
    this.grants = CollectionUtils.nullSafeMap(grants);
  }

  public static String noteGrantKey(final String notebookId, final String noteTitle) {
    return notebookId + ":" + noteTitle;
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
}
