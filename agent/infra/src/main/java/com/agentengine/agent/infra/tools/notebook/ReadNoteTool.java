package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class ReadNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.READ_NOTE,
          """
          Reads the current content of a note in a notebook you created or were granted.

          Returns: { status: "success", content } or { error }.""",
          Map.of());

  private final NotebookService notebookService;

  public ReadNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR);
    this.notebookService = notebookService;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description =
                  "The notebook to read from. Must be the exact notebook_id string you were "
                      + "granted (see your Notebook Access instructions) — never a shortened or "
                      + "human-friendly name.")
          final String notebookId,
      @ToolArg(name = Constants.ToolArgs.NOTE_TITLE, description = "The title of the note to read.")
          final String noteTitle) {
    final Note note = notebookService.getNote(notebookId, noteTitle);
    if (note == null) {
      return ToolOutput.direct(
          accessDeniedError(
              notebookService,
              "No such note: '%s', or you don't have access to its notebook."
                  .formatted(noteTitle)));
    }
    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.SUCCESS,
            "content",
            note.getContent()));
  }
}
