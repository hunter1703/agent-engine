package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class DeleteNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.DELETE_NOTE,
          """
          Permanently deletes a note you were granted edit_note access to, or one in a notebook you created.

          Returns: { status: "success" } or { error }.""",
          Map.of());

  private final NotesRepository notesRepository;

  public DeleteNoteTool(final NotesRepository notesRepository) {
    super(DESCRIPTOR);
    this.notesRepository = notesRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description = "The notebook the note is in.")
          final String notebookId,
      @ToolSchema(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "The title of the note to delete.")
          final String noteTitle) {
    final boolean owner = NotebookUtils.isOwner(notebookId, toolContext.sessionId());
    final NotebookGrants grants = grantsOf(toolContext);
    if (!owner && !NotebookUtils.canWrite(grants, notebookId, noteTitle)) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Not granted edit_note access to note '" + noteTitle + "' in this notebook.",
              "notebook_access",
              grantsSummary(toolContext)));
    }
    notesRepository.deleteById(NotebookUtils.noteId(notebookId, noteTitle));
    return ToolOutput.direct(Map.of("status", "success"));
  }
}
