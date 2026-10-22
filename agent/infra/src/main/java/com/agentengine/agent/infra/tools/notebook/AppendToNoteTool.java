package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class AppendToNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.APPEND_TO_NOTE,
          """
          Adds to an existing note, below a timestamp, to log progress without overwriting what is \
          there.
          Returns: { status: "success" }, or { error } on failure.""",
          Map.of());

  public AppendToNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR, notebookService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description =
                  "The exact ID of the notebook containing the note, as shown in your notebook list.")
          final String notebookId,
      @ToolArg(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "Title identifying the existing note within the notebook.")
          final String noteTitle,
      @ToolArg(
              name = Constants.ToolArgs.NOTE_CONTENT,
              description = "The text to add, stored exactly as written.")
          final String content) {
    if (!notebookService.canWriteNotes(notebookId)) {
      return ToolOutput.direct(
          accessDeniedError("You don't have access to write note '%s' in notebook '%s'."
                  .formatted(noteTitle, notebookId)));
    }
    final Note existing = notebookService.getNote(notebookId, noteTitle);
    if (existing == null) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Note '%s' does not exist in notebook '%s'. Create it first."
                  .formatted(noteTitle, notebookId)));
    }
    if (StringUtils.isBlank(content)) {
      return ToolOutput.direct(Map.of("error", "The text to add is empty."));
    }
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    try {
      notebookService.appendNote(
          notebookId,
          noteTitle,
          "[" + sessionState.formatCurrentTimeInUserZone() + "]\n" + content);
    } catch (final UnauthorizedException denied) {
      return ToolOutput.direct(
          accessDeniedError("You don't have access to write note '%s' in notebook '%s'."
                  .formatted(noteTitle, notebookId)));
    }
    syncReminder(toolContext, notebookId);
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }
}
