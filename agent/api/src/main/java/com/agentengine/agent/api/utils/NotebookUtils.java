package com.agentengine.agent.api.utils;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.util.agents.Constants;
import java.util.Locale;

public final class NotebookUtils {

  private NotebookUtils() {}

  public static String sanitize(final String input) {
    if (input == null) {
      return null;
    }
    return input.replace(Constants.ID_SEPARATOR, "-").toLowerCase(Locale.ROOT);
  }

  public static String notebookId(final String authorSession, final String name) {
    // authorSession (2 segments: agentId:uuid)
    return authorSession + Constants.ID_SEPARATOR + sanitize(name);
  }

  public static String noteId(final String notebookId, final String noteTitle) {
    return notebookId + Constants.ID_SEPARATOR + sanitize(noteTitle);
  }

  public static String notebookIdOf(final String noteId) {
    final int lastSeparator = noteId.lastIndexOf(Constants.ID_SEPARATOR);
    return lastSeparator < 0 ? null : noteId.substring(0, lastSeparator);
  }

  public static String noteTitleOf(final String noteId) {
    final int lastSeparator = noteId.lastIndexOf(Constants.ID_SEPARATOR);
    return lastSeparator < 0 ? null : noteId.substring(lastSeparator + 1);
  }

  public static boolean isNotebookId(final String id) {
    if (id == null) {
      return false;
    }
    // sessionId (2 segments: agentId:uuid) + name (1 segment) = 3
    return id.split(Constants.ID_SEPARATOR).length == 3;
  }

  public static boolean isNoteId(final String id) {
    if (id == null) {
      return false;
    }
    // notebookId (3 segments) + noteTitle (1 segment) = 4
    return id.split(Constants.ID_SEPARATOR).length == 4;
  }

  public static boolean isOwner(final String notebookId, final String sessionId) {
    return notebookId.startsWith(sessionId + Constants.ID_SEPARATOR);
  }

  public static boolean canRead(
      final NotebookGrants grants, final String notebookId, final String noteTitle) {
    return grants != null && grants.canRead(notebookId, noteTitle);
  }

  public static boolean canWrite(
      final NotebookGrants grants, final String notebookId, final String noteTitle) {
    return grants != null && grants.canWrite(notebookId, noteTitle);
  }

  public static boolean canCreate(final NotebookGrants grants, final String notebookId) {
    return grants != null && grants.canCreate(notebookId);
  }

  public static String normalizeGrantKey(final String key) {
    if (key == null) {
      return null;
    }
    final String[] parts = key.split(Constants.ID_SEPARATOR);
    if (parts.length != 3 && parts.length != 4) {
      return key;
    }
    // authorSession is always the first 2 segments (agentId:uuid).
    final String authorSession = parts[0] + Constants.ID_SEPARATOR + parts[1];
    final String notebookId = notebookId(authorSession, parts[2]);
    return parts.length == 3 ? notebookId : noteId(notebookId, parts[3]);
  }
}
