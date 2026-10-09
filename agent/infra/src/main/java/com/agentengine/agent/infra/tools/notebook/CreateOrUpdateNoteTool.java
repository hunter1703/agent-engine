package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CreateOrUpdateNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_OR_UPDATE_NOTE,
          """
          Creates or overwrites a note in a notebook. Use it for content meant to be read from the notebook — longer text, deliverables, anything another agent might need to consult — instead of relaying that text through your replies.

          The note's content comes in two steps: this call opens the note, and the message that follows asks you to write its content. Write that content as your reply, in plain text. The whole reply is saved as the note, so it holds the note and nothing else. After that, you continue your task.

          Returns: { status: "pending", message } — the note is open but not yet saved; the next message asks for its content. Or { error } if you can't write notes in the notebook.
          """,
          Map.of());

  private final NotebookService notebookService;

  public CreateOrUpdateNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR);
    this.notebookService = notebookService;
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
    if (!notebookService.canWriteNotes(notebookId)) {
      return ToolOutput.direct(
          accessDeniedError(
              notebookService,
              "You don't have access to write note '%s' in notebook '%s'."
                  .formatted(noteTitle, notebookId)));
    }
    final RunState runState = RunUtils.getRunState(toolContext.invocationContext());
    runState.startNote(notebookId, noteTitle);
    runState.addSignal(
        toolContext,
        new Signal<>(
            "note_body_" + NotebookUtils.noteId(notebookId, noteTitle),
            ("Write the content of note '%s' as your reply, in plain text. Your whole reply is"
                    + " saved as the note exactly as you write it, so it contains only the note"
                    + " itself: no tool call, no introduction, no closing remark.")
                .formatted(noteTitle),
            true));
    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.PENDING,
            "message",
            "Note '%s' is open. Its content is your next reply, which the next message asks for."
                .formatted(noteTitle)));
  }
}
