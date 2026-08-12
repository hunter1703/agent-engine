package com.agentengine.agent.core.services;

import com.agentengine.agent.api.services.CommunityRegistry;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.ResourceIndex;
import com.agentengine.util.common.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads expert agent definitions bundled as classpath resources under {@code
 * agent/core/src/main/resources/agents/community/experts}.
 *
 * <p>Each expert config file is read as a BaseAgentConfig. Experts are regular agent configs with
 * optional capabilities metadata for discovery.
 */
@Singleton
public final class CommunityRegistryImpl implements CommunityRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(CommunityRegistryImpl.class);
    private static final String EXPERTS_INDEX = "agents/community/experts/.index";

    private final LazyLoader<List<BaseAgentConfig>> experts;

    public CommunityRegistryImpl() {
        this.experts = new LazyLoader<>(this::loadExperts);
    }

    @Override
    public List<BaseAgentConfig> findExperts(final String query) {
        return experts.get();
    }

    @Override
    public BaseAgentConfig getExpert(final String id) {
        return experts.get().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst()
                .orElse(null);
    }

    private List<BaseAgentConfig> loadExperts() {
        final List<BaseAgentConfig> result = new ArrayList<>();
        final ResourceIndex index = new ResourceIndex(EXPERTS_INDEX);
        for (final String resourceName : index.listEntries()) {
            final String content = index.findContent(resourceName).orElse(null);
            if (StringUtils.isBlank(content)) {
                continue;
            }
            try {
                final BaseAgentConfig config = JsonUtils.fromJson(content, BaseAgentConfig.class);
                result.add(config);
                LOG.info("Loaded expert: {} ({})", config.getName(), config.getId());
            } catch (final Exception exception) {
                LOG.error("Failed to load expert from {}", resourceName, exception);
            }
        }
        LOG.info("Loaded {} expert(s) from {}", result.size(), EXPERTS_INDEX);
        return result;
    }
}
