package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.NotebookGrants;
import com.google.adk.tools.ToolContext;

public final class NotebookUtils {

  private NotebookUtils() {}

  public static String notebookId(final String author, final String name) {
    return author + Constants.ID_SEPARATOR + name;
  }

  public static String noteId(final String notebookId, final String noteTitle) {
    return notebookId + Constants.ID_SEPARATOR + noteTitle;
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
