package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class DeleteNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.DELETE_NOTE,
          """
          Permanently deletes a note.
          Returns: { status: "success" }, or { error } on failure.""",
          Map.of());

  public DeleteNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR, notebookService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description =
                  "The exact ID of the notebook the note is in, as shown in your notebook list.")
          final String notebookId,
      @ToolArg(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "The title of the note to delete.")
          final String noteTitle) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    if (!notebookService.deleteNote(notebookId, noteTitle)) {
      return ToolOutput.direct(
          accessDeniedError("No such note '%s' in this notebook, or you aren't allowed to delete it."
                  .formatted(noteTitle)));
    }
    if (notebookService.isScratchpad(notebookId)) {
      sessionState.syncScratchpadReminder(notebookService);
    } else {
      sessionState.syncNotebookReminder(notebookService);
    }
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }
}
