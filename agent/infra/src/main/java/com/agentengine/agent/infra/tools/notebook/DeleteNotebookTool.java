package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class DeleteNotebookTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.DELETE_NOTEBOOK,
          """
          Permanently deletes a notebook and every note in it. Only the session that created the \
          notebook can delete it.
          Returns: { status: "success" }, or { error } on failure.""",
          Map.of());

  public DeleteNotebookTool(final NotebookService notebookService) {
    super(DESCRIPTOR, notebookService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description = "The ID of the notebook to delete.")
          final String notebookId) {
    if (!notebookService.deleteNotebook(notebookId)) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "No such notebook '%s', or you aren't allowed to delete it.".formatted(notebookId)));
    }
    SessionUtils.getSessionState(toolContext.invocationContext())
        .syncNotebookReminder(notebookService);
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }
}
