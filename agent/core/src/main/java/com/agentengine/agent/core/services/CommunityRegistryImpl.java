package com.agentengine.agent.core.services;

import com.agentengine.agent.api.services.CommunityRegistry;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.LazyLoader;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads expert agent definitions from the configs/agents/community/experts directory.
 *
 * <p>Each expert config file (JSON or YAML) is read as a BaseAgentConfig. Experts are regular
 * agent configs with optional capabilities metadata for discovery.
 */
@Singleton
public final class CommunityRegistryImpl implements CommunityRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(CommunityRegistryImpl.class);
    private static final String EXPERTS_DIR = "configs/agents/community/experts";

    private final LazyLoader<List<BaseAgentConfig>> experts;

    public CommunityRegistryImpl() {
        this.experts = new LazyLoader<>(this::loadExperts);
    }

    @Override
    public List<BaseAgentConfig> findExperts(final String query) {
        return experts.get();
    }

    private List<BaseAgentConfig> loadExperts() {
        final List<BaseAgentConfig> result = new ArrayList<>();
        final Path expertsPath = Paths.get(EXPERTS_DIR);

        if (!Files.exists(expertsPath) || !Files.isDirectory(expertsPath)) {
            LOG.warn("Experts directory does not exist: {}", EXPERTS_DIR);
            return result;
        }

        try (Stream<Path> paths = Files.walk(expertsPath, 1)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    final BaseAgentConfig config = JsonUtils.fromFile(path, BaseAgentConfig.class);
                    result.add(config);
                    LOG.info("Loaded expert: {} ({})", config.getName(), config.getId());
                } catch (final Exception exception) {
                    LOG.error("Failed to load expert from {}", path, exception);
                }
            });
        } catch (final IOException exception) {
            LOG.error("Failed to scan experts directory: {}", EXPERTS_DIR, exception);
        }

        LOG.info("Loaded {} expert(s) from {}", result.size(), EXPERTS_DIR);
        return result;
    }
}
