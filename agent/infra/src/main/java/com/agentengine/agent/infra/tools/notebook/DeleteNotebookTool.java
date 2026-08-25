package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Query;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class DeleteNotebookTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.DELETE_NOTEBOOK_TOOL_NAME,
          "Permanently deletes an entire notebook and every note in it. Only the session that "
              + "created the notebook may delete it — no grant covers this, since it destroys the "
              + "whole shared space, not just what you were given access to. "
              + "Returns: { status: \"success\" } or { error }.",
          Map.of());

  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;

  public DeleteNotebookTool(
      final NotebookRepository notebookRepository, final NotesRepository notesRepository) {
    super(DESCRIPTOR);
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(name = Constants.ARG_NOTEBOOK_ID, description = "The notebook to delete.")
          final String notebookId) {
    if (!NotebookUtils.isOwner(notebookId, toolContext.sessionId())) {
      return ToolOutput.direct(
          Map.of("error", "Only the session that created this notebook may delete it."));
    }
    notebookRepository.deleteById(notebookId);
    notesRepository.deleteByQuery(
        new Query().withFilter(Filters.eq(Note.FIELD_NOTEBOOK_ID, notebookId)));
    return ToolOutput.direct(Map.of("status", "success"));
  }
}
