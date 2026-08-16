package com.agentengine.agent.api.services;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import java.util.List;

/** Registry of expert agents available in the community. */
public interface CommunityRegistry {

  String MEMORY_AGENT = "memory-agent";

  /**
   * Returns all registered experts in the community.
   *
   * @return list of all expert agent configs
   */
  List<BaseAgentConfig> findExperts(String query);

  /**
   * Returns the config for a well-known expert by ID, or {@code null} if not found.
   *
   * @param id one of the public constants defined on this interface (e.g. {@link #MEMORY_AGENT})
   */
  BaseAgentConfig getExpert(String id);
}
