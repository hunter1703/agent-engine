package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractNotebookTool extends Tool {

  protected AbstractNotebookTool(ToolDescriptor toolDescriptor) {
    super(toolDescriptor);
  }

  protected static Map<String, Object> accessDeniedError(
      final NotebookService notebookService, final String message) {
    final Map<String, Object> error = new LinkedHashMap<>();
    error.put("error", message);
    error.put("hint", "The notebooks you can access:\n" + notebookService.summary());
    return error;
  }
}
