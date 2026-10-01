package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.NotebookService;
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
  public NotebookToolsetProvider(final NotebookService notebookService) {
    super(
        TOOLSET_DESCRIPTOR,
        List.of(
            new ToolDefinition(
                CreateNotebookTool.DESCRIPTOR, () -> new CreateNotebookTool(notebookService)),
            new ToolDefinition(
                CreateOrUpdateNoteTool.DESCRIPTOR,
                () -> new CreateOrUpdateNoteTool(notebookService)),
            new ToolDefinition(ReadNoteTool.DESCRIPTOR, () -> new ReadNoteTool(notebookService)),
            new ToolDefinition(
                DeleteNoteTool.DESCRIPTOR, () -> new DeleteNoteTool(notebookService)),
            new ToolDefinition(
                DeleteNotebookTool.DESCRIPTOR, () -> new DeleteNotebookTool(notebookService))));
  }
}
