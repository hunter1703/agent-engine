package com.agentengine.agent.infra.tools.agent;

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

public class RefreshRemindersTool extends Tool {

  private final ReminderSyncService reminderSyncService;

  public RefreshRemindersTool(final ReminderSyncService reminderSyncService) {
    super(
        new ToolDescriptor(
            Constants.ToolNames.REFRESH_REMINDERS,
            "Returns your current reminders: the notebooks, knowledge and anything else they list"
                + " that you can work with. Call it if you suspect they changed in the background, or"
                + " if access to something you should be able to reach fails. Each result replaces"
                + " the reminders shown earlier, and earlier results are marked expired."));
    this.reminderSyncService = reminderSyncService;
  }

  public ToolOutput<Map<String, Object>> execute(final ToolContext toolContext) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());

    reminderSyncService.syncAll(sessionState);

    final String formattedReminders = ReminderPlugin.buildBrief(sessionState.reminders());

    return ToolOutput.direct(
        Map.of(
            "status", "success",
            "message",
            "Your current reminders. They replace any shown earlier.",
            "reminders",
            formattedReminders == null ? "You have no reminders right now." : formattedReminders));
  }
}
