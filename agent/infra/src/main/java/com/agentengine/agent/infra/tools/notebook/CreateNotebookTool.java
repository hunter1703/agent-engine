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
                      Creates a new, empty notebook — a shared space where titled notes can be read across sessions. You have full access to a notebook you create; other sessions reach it only once you grant it to them when you communicate with them. Use the returned notebook_id with create_note to add notes.
                      Returns: { status: "success", notebook_id }, or { error } if the name is already in use.""",
          Map.of());

  private final NotebookService notebookService;

  public CreateNotebookTool(final NotebookService notebookService) {
    super(DESCRIPTOR);
    this.notebookService = notebookService;
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
                  What this notebook is for, so other agents granted access can understand its purpose without you having to explain it again.""",
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
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.SUCCESS,
            Constants.ToolArgs.NOTEBOOK_ID,
            notebook.getId(),
            "message",
            "Notebook '%s' successfully created".formatted(notebook.getId())));
  }
}
