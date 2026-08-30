package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.Notebook;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CreateNotebookTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_NOTEBOOK,
          """
          Creates a new, empty notebook — a shared space for titled notes that other agent sessions can be granted access to. You always have full read/write/delete access to a notebook you create; grant access to it for other sessions via the %s field on %s or %s.

          Returns: { status: "success", notebook_id }, or { error } if that name is already in use.\
          """
              .formatted(
                  Constants.ToolArgs.NOTEBOOK_GRANTS,
                  Constants.ToolNames.SPAWN_AGENT,
                  Constants.ToolNames.SEND_MESSAGE),
          Map.of());

  private final NotebookRepository notebookRepository;

  public CreateNotebookTool(final NotebookRepository notebookRepository) {
    super(DESCRIPTOR);
    this.notebookRepository = notebookRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(
              name = "name",
              description =
                  """
                  A short, memorable name for this notebook. This becomes its notebook_id — the exact value you and every agent you grant access to will use to reference it.""")
          final String name,
      @ToolSchema(
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
      notebookRepository.insert(notebook);
    } catch (final DuplicateAssetException exception) {
      return ToolOutput.direct(Map.of("error", "Notebook '" + name + "' already exists."));
    }
    return ToolOutput.direct(
        Map.of(
            "status",
            "success",
            "notebook_id",
            notebook.getId(),
            "message",
            "Notebook '%s' successfully created".formatted(notebook.getId())));
  }
}
