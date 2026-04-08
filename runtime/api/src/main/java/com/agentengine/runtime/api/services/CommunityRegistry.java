package com.agentengine.runtime.api.services;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import java.util.List;

/** Registry of expert agents available in the community. */
public interface CommunityRegistry {

    /**
     * Returns all registered experts in the community.
     *
     * @return list of all expert agent configs
     */
    List<BaseAgentConfig> findExperts(String query);
}
