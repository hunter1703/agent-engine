package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;
import java.util.LinkedHashMap;
import java.util.Map;

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

  protected static Map<String, Object> accessDeniedError(
      final ToolContext toolContext, final String message, final String attemptedNotebookId) {
    final NotebookGrants grants = grantsOf(toolContext);
    final Map<String, Object> error = new LinkedHashMap<>();
    error.put("error", message);
    if (grants != null) {
      final String suggestion = grants.suggestNotebookId(attemptedNotebookId);
      if (suggestion != null) {
        error.put(
            "hint",
            "notebook_id '"
                + attemptedNotebookId
                + "' doesn't match any of your grants. Did you mean '"
                + suggestion
                + "'? Use that exact string as notebook_id, not a shortened name.");
      }
    }
    error.put("notebook_access", grantsSummary(toolContext));
    return error;
  }
}
