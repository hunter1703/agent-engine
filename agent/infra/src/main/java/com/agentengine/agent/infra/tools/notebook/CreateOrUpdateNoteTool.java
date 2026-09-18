package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.annotations.ToolArg;
import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.beans.Permission;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CreateOrUpdateNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_OR_UPDATE_NOTE,
          """
          Creates or overwrites a note in a notebook. Use it for content meant to be read from the notebook — longer text, deliverables, anything another agent might need to consult — instead of relaying that text through your replies.

          The note body arrives in two steps: this call begins the note, and your very next reply supplies the content in full — no preamble, no sign-off, nothing else. Once the reply lands, the note is saved and you'll resume your task automatically.

          Returns: { status: "awaiting_body", message } — the note is not yet saved; your next reply supplies its body. Or { error } if you lack access (notebook-wide for a new title, edit access to an existing one).
          """,
          Map.of());

  private final NotesRepository notesRepository;

  public CreateOrUpdateNoteTool(final NotesRepository notesRepository) {
    super(DESCRIPTOR);
    this.notesRepository = notesRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description =
                  "The notebook to add this note to. Must be the exact notebook_id string you "
                      + "were granted or that create_notebook returned — never a shortened or "
                      + "human-friendly name.")
          final String notebookId,
      @ToolArg(
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
            accessDeniedError(
                toolContext,
                "Note '%s' already exists and you don't have edit access to overwrite it."
                    .formatted(noteTitle)));
      }
      if (!noteExists && !NotebookUtils.canCreate(grants, notebookId)) {
        return ToolOutput.direct(
            accessDeniedError(
                toolContext,
                "You don't have notebook-wide access to add a note to this notebook."));
      }
    }
    RunUtils.getRunState(toolContext.invocationContext()).startNote(notebookId, noteTitle);
    SessionUtils.getSessionState(toolContext.invocationContext())
        .addNotebookReminders(NotebookGrants.ofNote(notebookId, noteTitle, Permission.WRITE));
    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.PENDING,
            "message",
            "Write the note body now. Your next reply becomes the full note content — no preamble, no sign-off, nothing else. You'll resume your task once it's saved."));
  }
}
