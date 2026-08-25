package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.tools.AbstractToolsetProvider;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public final class NotebookToolsetProvider extends AbstractToolsetProvider {

  private static final ToolDescriptor TOOLSET_DESCRIPTOR =
      new ToolDescriptor(
          Constants.Toolsets.NOTEBOOK,
          "Tools for creating notebooks and titled notes shared across agent sessions.",
          Map.of());

  @Inject
  public NotebookToolsetProvider(
      final NotebookRepository notebookRepository, final NotesRepository notesRepository) {
    super(
        TOOLSET_DESCRIPTOR,
        List.of(
            new ToolDefinition(
                CreateNotebookTool.DESCRIPTOR, () -> new CreateNotebookTool(notebookRepository)),
            new ToolDefinition(CreateNoteTool.DESCRIPTOR, CreateNoteTool::new),
            new ToolDefinition(ReadNoteTool.DESCRIPTOR, () -> new ReadNoteTool(notesRepository)),
            new ToolDefinition(
                DeleteNoteTool.DESCRIPTOR, () -> new DeleteNoteTool(notesRepository)),
            new ToolDefinition(
                DeleteNotebookTool.DESCRIPTOR,
                () -> new DeleteNotebookTool(notebookRepository, notesRepository))));
  }
}
