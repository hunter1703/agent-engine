package com.agentengine.agent.api.services;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import java.util.List;

public interface CommunityExpertsService {

  String MEMORY_AGENT = "memory-agent";
  String VISION_AGENT = "vision-agent";

  /** Returns all registered experts in the community. */
  List<BaseAgentConfig> findExperts(String query);

  /**
   * Returns the config for a well-known expert by ID, or {@code null} if not found.
   *
   * @param id one of the public constants defined on this interface (e.g. {@link #MEMORY_AGENT})
   */
  BaseAgentConfig getExpert(String id);

  /** Runs {@code id} for a single turn, returning its response text. */
  String invokeExpert(String id, String modelId, UserMessage userMessage);
}
