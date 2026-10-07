package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.infra.plugins.KnowledgePlugin;
import com.agentengine.agent.infra.plugins.NotebookPlugin;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.knowledge.core.service.KnowledgeService;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.google.adk.tools.ToolContext;
import com.google.adk.tools.ToolDescriptor;
import com.google.adk.tools.ToolOutput;

import java.util.Map;

public class RefreshRemindersTool extends AbstractAgentTool {

  public static final String TOOL_ID = "refresh_reminders";

  public RefreshRemindersTool(
      final NotebookService notebookService, final KnowledgeService knowledgeService) {
    super(
        ToolDescriptor.builder()
            .name(TOOL_ID)
            .description(
                "Refreshes your working memory reminders regarding shared resources (notebooks, knowledge). "
                    + "Call this tool if you suspect the environment has changed in the background or if you "
                    + "encounter access errors (e.g., RBAC errors) for resources that you believe you should have access to. "
                    + "Once called, your working memory at the top of the context will be updated to reflect the latest state.")
            .build(),
        null, // actorSystemProvider not needed
        notebookService,
        knowledgeService,
        null); // accessControlService not needed
  }

  @Override
  public ToolOutput<Map<String, Object>> execute(final ToolContext toolContext) {
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
            "message", "Reminders have been successfully synchronized with the latest database state. Check your working memory for the updated access lists."));
  }
}
