package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;

public abstract class AbstractNotebookTool extends Tool {

  protected AbstractNotebookTool(ToolDescriptor toolDescriptor) {
    super(toolDescriptor);
  }

  protected static NotebookGrants grantsOf(final ToolContext toolContext) {
    return toolContext.invocationContext().runConfig() instanceof ExtendedRunConfig extended
        ? extended.grants().notebookGrants()
        : null;
  }
}
