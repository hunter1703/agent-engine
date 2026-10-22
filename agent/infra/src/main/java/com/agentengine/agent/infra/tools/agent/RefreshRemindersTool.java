package com.agentengine.agent.infra.tools.agent;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.plugins.ReminderPlugin;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.google.adk.tools.ToolContext;
import java.util.Map;

@DiscoverableTool
public class RefreshRemindersTool extends Tool {

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.REFRESH_REMINDERS,
          "Returns your reminders as they are now: the notebooks, knowledge and anything else they"
              + " list. Call it when you suspect they changed elsewhere, or when access to something"
              + " you should have fails. Each result replaces the earlier ones.");

  private final ReminderSyncService reminderSyncService;

  public RefreshRemindersTool(final ReminderSyncService reminderSyncService) {
    super(DESCRIPTOR);
    this.reminderSyncService = reminderSyncService;
  }

  public ToolOutput<Map<String, Object>> execute(final ToolContext toolContext) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());

    reminderSyncService.syncAll(sessionState);

    final String formattedReminders = ReminderPlugin.buildBrief(sessionState.allReminders());

    return ToolOutput.direct(
        Map.of(
            "reminders",
            formattedReminders == null ? "You have no reminders right now." : formattedReminders));
  }
}
