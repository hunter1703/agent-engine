package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.NotebookGrants;
import com.google.adk.tools.ToolContext;

public final class NotebookUtils {

  private NotebookUtils() {}

  public static String sanitize(final String input) {
    if (input == null) {
      return null;
    }
    return input.replace(Constants.ID_SEPARATOR, "-");
  }

  public static String notebookId(final String authorSession, final String name) {
    // authorSession (2 segments: agentId:uuid)
    return authorSession + Constants.ID_SEPARATOR + sanitize(name);
  }

  public static String noteId(final String notebookId, final String noteTitle) {
    return notebookId + Constants.ID_SEPARATOR + sanitize(noteTitle);
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

  public static NotebookGrants grantsOf(final ToolContext toolContext) {
    return toolContext.invocationContext().runConfig() instanceof ExtendedRunConfig extended
        ? extended.grants().notebookGrants()
        : null;
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
}
