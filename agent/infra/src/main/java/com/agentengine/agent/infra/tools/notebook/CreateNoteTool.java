package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CreateNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_NOTE,
          """
          Stages a note to be saved into a notebook, so other agents granted access to it can read it instead of you having to relay its full text yourself — a title that doesn't exist yet in the notebook is created fresh, one that already exists is overwritten with the new content. Call this immediately before writing the note's content — not before a clarification or a partial draft — then write that content as your very next message with nothing else in between; it is saved automatically once you do, and you can then continue your task or give a final answer.

          Returns: { status: "pending", message } — write the note's content next, or { error } if you're missing the access this call needs: create_note access to this notebook for a title that doesn't exist yet, or edit_note access to the note if that title already exists.""",
          Map.of());

  private final NotesRepository notesRepository;

  public CreateNoteTool(final NotesRepository notesRepository) {
    super(DESCRIPTOR);
    this.notesRepository = notesRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description = "The notebook to add this note to.")
          final String notebookId,
      @ToolSchema(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "Short title identifying this note within the notebook.")
          final String noteTitle) {
    final boolean owner = NotebookUtils.isOwner(notebookId, toolContext.sessionId());
    final NotebookGrants grants = grantsOf(toolContext);
    final boolean noteExists =
        notesRepository.findById(NotebookUtils.noteId(notebookId, noteTitle)) != null;
    if (!owner) {
      if (noteExists && !NotebookUtils.canWrite(grants, notebookId, noteTitle)) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "Note '" + noteTitle + "' already exists — not granted edit_note access to it."));
      }
      if (!noteExists && !NotebookUtils.canCreate(grants, notebookId)) {
        return ToolOutput.direct(
            Map.of("error", "Not granted create_note access to this notebook."));
      }
    }
    RunUtils.getRunState(toolContext.invocationContext()).startNote(notebookId, noteTitle);
    return ToolOutput.direct(
        Map.of(
            "status",
            "pending",
            "message",
            """
            Saving started — write this note's content as your very next message, with nothing else first."""));
  }
}
