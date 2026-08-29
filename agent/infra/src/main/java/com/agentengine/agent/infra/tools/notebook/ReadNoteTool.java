package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class ReadNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.READ_NOTE,
          "Reads the current content of a note you were granted read_note or edit_note access "
              + "to, or one in a notebook you created. "
              + "Returns: { status: \"success\", content } or { error }.",
          Map.of());

  private final NotesRepository notesRepository;

  public ReadNoteTool(final NotesRepository notesRepository) {
    super(DESCRIPTOR);
    this.notesRepository = notesRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(name = Constants.ToolArgs.NOTEBOOK_ID, description = "The notebook to read from.")
          final String notebookId,
      @ToolSchema(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "The title of the note to read.")
          final String noteTitle) {
    final boolean owner = NotebookUtils.isOwner(notebookId, toolContext.sessionId());
    final NotebookGrants grants = grantsOf(toolContext);
    if (!owner && !NotebookUtils.canRead(grants, notebookId, noteTitle)) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Not granted read_note access to note '" + noteTitle + "' in this notebook."));
    }
    final Note note = notesRepository.findById(NotebookUtils.noteId(notebookId, noteTitle));
    if (note == null) {
      return ToolOutput.direct(Map.of("error", "No such note: '" + noteTitle + "'."));
    }
    return ToolOutput.direct(Map.of("status", "success", "content", note.getContent()));
  }
}
