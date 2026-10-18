package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.utils.StringUtils;
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
    final String summary = notebookService != null ? notebookService.summary(false) : null;
    final String scratchpadHint =
        "For your session's private working memory, use notebookId: 'scratchpad'.";
    final String hint =
        StringUtils.isNotBlank(summary)
            ? "The notebooks you can access:\n- " + summary + "\n" + scratchpadHint
            : scratchpadHint;
    error.put("hint", hint);
    return error;
  }
}
