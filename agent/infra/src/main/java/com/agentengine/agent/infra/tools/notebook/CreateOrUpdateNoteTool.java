package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CreateOrUpdateNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_OR_UPDATE_NOTE,
          """
          Creates a note, or overwrites it. Use it for findings and data you will need in later steps \
          or after earlier turns are summarised. The final answer belongs in your reply, not in a note.
          Returns: { status: "success" }, or { error } on failure.""",
          Map.of());

  public CreateOrUpdateNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR, notebookService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description =
                  "The exact ID of the notebook to add this note to, as shown in your notebook list.")
          final String notebookId,
      @ToolArg(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "Short title identifying this note within the notebook.")
          final String noteTitle,
      @ToolArg(
              name = Constants.ToolArgs.NOTE_CONTENT,
              description = "The full text of the note, stored exactly as written.")
          final String content) {
    if (!notebookService.canWriteNotes(notebookId)) {
      return ToolOutput.direct(
          accessDeniedError("You don't have access to write note '%s' in notebook '%s'."
                  .formatted(noteTitle, notebookId)));
    }
    if (StringUtils.isBlank(content)) {
      return ToolOutput.direct(Map.of("error", "The note's text is empty."));
    }
    try {
      final Note existing = notebookService.getNote(notebookId, noteTitle);
      notebookService.saveNote(
          notebookId, noteTitle, content, existing == null ? null : existing.getVersion());
    } catch (final UnauthorizedException denied) {
      return ToolOutput.direct(
          accessDeniedError("You don't have access to write note '%s' in notebook '%s'."
                  .formatted(noteTitle, notebookId)));
    } catch (final StaleStateException | DuplicateAssetException changed) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Note '%s' was not saved: its content changed meanwhile. Read it first, then write it again."
                  .formatted(noteTitle)));
    }
    syncReminder(toolContext, notebookId);
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }
}
