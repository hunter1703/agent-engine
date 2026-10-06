package com.agentengine.agent.infra.tools.agent;

import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RefreshRemindersTool extends Tool {

  private final ReminderSyncService reminderSyncService;

  public RefreshRemindersTool(final ReminderSyncService reminderSyncService) {
    super(
        new ToolDescriptor(
                Constants.ToolNames.REFRESH_REMINDERS,
            "Refreshes your working memory reminders regarding shared resources (notebooks, knowledge, etc.). "
                + "Call this tool if you suspect the environment has changed in the background or if you "
                + "encounter access errors (e.g., RBAC errors) for resources that you believe you should have access to. "
                + "Note: Calling this tool multiple times will automatically mark previous refresh results as 'expired'. "
                + "Always rely on the most recent successful tool response as the definitive, up-to-date state of your access."));
    this.reminderSyncService = reminderSyncService;
  }

  public ToolOutput<Map<String, Object>> execute(final ToolContext toolContext) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());

    reminderSyncService.syncAll(sessionState);

    final String formattedReminders = com.agentengine.agent.infra.plugins.ReminderPlugin.formatReminders(sessionState.reminders());

    return ToolOutput.direct(Map.of(
            "status", "success",
            "message", "Reminders have been successfully synchronized with the latest database state.",
            "reminders_brief", formattedReminders == null ? "No reminders active." : formattedReminders));
  }
}
