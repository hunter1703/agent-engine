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

  /**
   * Renders the caller's actual notebook grants, for including alongside an access-denied error so
   * the model can see its real notebook/note ids and self-correct instead of repeating the same
   * wrong id.
   */
  protected static String grantsSummary(final ToolContext toolContext) {
    final NotebookGrants grants = grantsOf(toolContext);
    return grants == null ? "You have no notebook access." : grants.describe();
  }
}
