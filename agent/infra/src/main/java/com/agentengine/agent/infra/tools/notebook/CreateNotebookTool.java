package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.Notebook;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CreateNotebookTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_NOTEBOOK,
          """
          Creates an empty notebook for titled notes.
          Returns: { notebook_id }, or { error } on failure.""",
          Map.of());

  public CreateNotebookTool(final NotebookService notebookService) {
    super(DESCRIPTOR, notebookService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = "name",
              description =
                  """
                  A short, memorable name for this notebook.""")
          final String name,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_DESCRIPTION,
              description =
                  """
                  Describes the notebook for anyone choosing among notebooks: its purpose, the kind of notes it holds, and what does not belong in it.""",
              optional = true)
          final String description) {
    if (StringUtils.isBlank(name)) {
      return ToolOutput.direct(Map.of("error", "Notebook name is required."));
    }
    final String sessionId = toolContext.sessionId();
    Notebook notebook =
        new Notebook(sessionId, name, StringUtils.isBlank(description) ? "" : description);
    try {
      notebookService.createNotebook(notebook);
    } catch (final DuplicateAssetException exception) {
      return ToolOutput.direct(Map.of("error", "Notebook '" + name + "' already exists."));
    }
    SessionUtils.getSessionState(toolContext.invocationContext())
        .syncNotebookReminder(notebookService);
    return ToolOutput.direct(
        Map.of(Constants.ToolArgs.NOTEBOOK_ID, notebook.getId()));
  }
}
