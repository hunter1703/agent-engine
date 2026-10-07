package com.agentengine.agent.infra.reminders;

import com.agentengine.agent.infra.utils.SessionState;

/** Interface for services that synchronize reminders with the current SessionState. */
public interface ReminderSyncer {
  void sync(SessionState sessionState);
}
