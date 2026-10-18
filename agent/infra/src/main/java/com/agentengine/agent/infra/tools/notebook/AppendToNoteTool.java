package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.notebook.ScratchpadNotebook;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class AppendToNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.APPEND_TO_NOTE,
          """
          Appends content to an existing note in a notebook. Use it to log updates, add new sections, or record incremental progress, thoughts, and drafts without overwriting previous content.

          Only append when information must be retained across future steps; do not append transient details already in immediate context.

          The appended content comes in two steps: this call opens the note for append, and the message that follows asks you to write the content to append. Write that content as your reply, in plain text. The whole reply is appended to the note. After that, you continue your task.

          Returns: { status: "pending", message } — the note is open for append; the next message asks for the content. Or { error } if the note does not exist or you can't write notes in the notebook.
          """,
          Map.of());

  private final NotebookService notebookService;

  public AppendToNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR);
    this.notebookService = notebookService;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description =
                  "The ID of the notebook containing the note. Use 'scratchpad' for your private working memory when you need to retain intermediate findings for later steps, or the exact notebook ID from your active notebook list.")
          final String notebookId,
      @ToolArg(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "Title identifying the existing note within the notebook.")
          final String noteTitle) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final boolean isScratchpad = sessionState.isScratchpad(notebookId);
    if (!isScratchpad && !notebookService.canWriteNotes(notebookId)) {
      return ToolOutput.direct(
          accessDeniedError(
              notebookService,
              "You don't have access to write note '%s' in notebook '%s'."
                  .formatted(noteTitle, notebookId)));
    }
    final Note existing;
    if (isScratchpad) {
      final ScratchpadNotebook scratchpad = sessionState.scratchpadNotebook();
      existing = scratchpad.getNote(noteTitle);
    } else {
      existing = notebookService.getNote(notebookId, noteTitle);
    }
    if (existing == null) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Note '%s' does not exist in notebook '%s'. Use %s to create it first."
                  .formatted(noteTitle, notebookId, Constants.ToolNames.CREATE_OR_UPDATE_NOTE)));
    }
    final RunState runState = RunUtils.getRunState(toolContext.invocationContext());
    runState.startNote(notebookId, noteTitle, true);
    runState.addSignal(
        toolContext,
        new Signal<>(
            "note_body_" + NotebookUtils.noteId(notebookId, noteTitle),
            ("Write the content to append to note '%s' as your reply, in plain text. Your whole"
                    + " reply is appended to the note exactly as you write it: no tool call, no"
                    + " introduction, no closing remark.")
                .formatted(noteTitle),
            true));
    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.PENDING,
            "message",
            "Note '%s' is open for append. The next message asks for the content to append."
                .formatted(noteTitle)));
  }
}
