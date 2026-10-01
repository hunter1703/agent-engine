package com.agentengine.catalog.core.services;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.context.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Deletes the schedules of deleted agents, with their scheduler jobs, as the system: a schedule of
 * an agent that no longer exists can never run, whoever created it. Agents are deleted by id; a
 * delete by filter is passed over.
 */
@Singleton
public class AgentScheduleCleanupListener implements EntityChangeListener<BaseAgentConfig> {

  private final RuntimeService runtimeService;

  @Inject
  public AgentScheduleCleanupListener(final RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @Override
  public Class<BaseAgentConfig> entityClass() {
    return BaseAgentConfig.class;
  }

  @Override
  public void onChange(final EntityChange<BaseAgentConfig> change) {
    if (change.type() == EntityChange.Type.DELETED
        && change instanceof EntityChange.Ids<BaseAgentConfig> deleted) {
      Context.require()
          .asSystemCaller()
          .run(() -> runtimeService.deleteAgentSchedules(deleted.ids()));
    }
  }
}
