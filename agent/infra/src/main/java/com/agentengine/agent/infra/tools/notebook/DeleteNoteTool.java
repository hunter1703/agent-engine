package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class DeleteNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.DELETE_NOTE,
          """
          Permanently deletes a note you were granted edit access to, or one in a notebook you created.

          Returns: { status: "success" } or { error }.""",
          Map.of());

  private final NotesRepository notesRepository;

  public DeleteNoteTool(final NotesRepository notesRepository) {
    super(DESCRIPTOR);
    this.notesRepository = notesRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(name = Constants.ToolArgs.NOTEBOOK_ID, description = "The notebook the note is in.")
          final String notebookId,
      @ToolArg(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "The title of the note to delete.")
          final String noteTitle) {
    final boolean owner = NotebookUtils.isOwner(notebookId, toolContext.sessionId());
    final NotebookGrants grants = grantsOf(toolContext);
    if (!owner && !NotebookUtils.canWrite(grants, notebookId, noteTitle)) {
      return ToolOutput.direct(
          accessDeniedError(
              toolContext,
              "You don't have edit access to note '%s' in this notebook.".formatted(noteTitle)));
    }
    notesRepository.deleteById(NotebookUtils.noteId(notebookId, noteTitle));
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }
}
