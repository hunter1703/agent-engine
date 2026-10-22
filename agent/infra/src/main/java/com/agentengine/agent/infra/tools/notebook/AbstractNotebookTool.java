package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.google.adk.tools.ToolContext;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.utils.StringUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractNotebookTool extends Tool {

  protected final NotebookService notebookService;

  protected AbstractNotebookTool(
      final ToolDescriptor toolDescriptor, final NotebookService notebookService) {
    super(toolDescriptor);
    this.notebookService = notebookService;
  }

  /** Brings the notebook reminder up to date after a note in {@code notebookId} changed. */
  protected void syncReminder(final ToolContext toolContext, final String notebookId) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    if (notebookService.isScratchpad(notebookId)) {
      sessionState.syncScratchpadReminder(notebookService);
    } else {
      sessionState.syncNotebookReminder(notebookService);
    }
  }

  protected Map<String, Object> accessDeniedError(final String message) {
    final Map<String, Object> error = new LinkedHashMap<>();
    error.put("error", message);
    final List<String> summaries = new ArrayList<>();
    final String scratchpad = notebookService.scratchpadSummary();
    if (StringUtils.isNotBlank(scratchpad)) {
      summaries.add(scratchpad);
    }
    summaries.addAll(notebookService.summaries(false).values());
    if (!summaries.isEmpty()) {
      error.put(
          "hint",
          "The notebooks you can access:\n"
              + summaries.stream().map(text -> "- " + text).collect(Collectors.joining("\n")));
    }
    return error;
  }
}
