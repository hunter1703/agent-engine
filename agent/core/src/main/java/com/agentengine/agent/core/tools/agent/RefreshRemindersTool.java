package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public class RefreshRemindersTool extends AbstractAgentTool {

  public static final String TOOL_ID = "refresh_reminders";

  public RefreshRemindersTool(
      final NotebookService notebookService, final KnowledgeService knowledgeService) {
    super(
        new ToolDescriptor(
            TOOL_ID,
            "Refreshes your working memory reminders regarding shared resources (notebooks, knowledge). "
                + "Call this tool if you suspect the environment has changed in the background or if you "
                + "encounter access errors (e.g., RBAC errors) for resources that you believe you should have access to. "
                + "Once called, your working memory at the top of the context will be updated to reflect the latest state."),
        null, // actorSystemProvider not needed
        notebookService,
        knowledgeService,
        null, // accessControlService not needed
        null); // reminderSyncService not needed
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());

    if (notebookService != null) {
      sessionState.syncNotebookReminder(notebookService);
    }

    if (knowledgeService != null) {
      sessionState.syncKnowledgeReminders(knowledgeService);
    }

    return ToolOutput.direct(
        Map.of(
            "status", "success",
            "message",
                "Reminders have been successfully synchronized with the latest database state. Check your working memory for the updated access lists."));
  }
}
