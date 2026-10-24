package com.agentengine.agent.infra.tools.agent;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.tools.Tool;
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
          "Brings your reminders up to date with what changed elsewhere, the notebooks, knowledge and"
              + " anything else they list; they reach you with your next request. Call it when you"
              + " suspect they changed elsewhere, or when access to something you should have fails."
              + "\nReturns: { status: \"success\" }.");

  private final ReminderSyncService reminderSyncService;

  public RefreshRemindersTool(final ReminderSyncService reminderSyncService) {
    super(DESCRIPTOR);
    this.reminderSyncService = reminderSyncService;
  }

  public ToolOutput<Map<String, Object>> execute(final ToolContext toolContext) {
    reminderSyncService.syncAll(SessionUtils.getSessionState(toolContext.invocationContext()));
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }

  @Override
  protected boolean citesTask() {
    return false;
  }
}
