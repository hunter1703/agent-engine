package com.agentengine.interfaces.local.bootstrap;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.mongodb.infra.DefaultModelConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Bootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(Bootstrapper.class);

  @ConfigProperty(name = "agent.engine.bootstrap.dir", defaultValue = "configs")
  String bootstrapDir;

  @Inject
  AgentService agentService;

  @Inject
  ModelService modelService;

  @Inject
  InfraMongoRepository infraMongoRepository;

  void onStart(@Observes StartupEvent ev) {
    LOG.info("Starting data bootstrapping from directory: {}", bootstrapDir);
    Path root = Paths.get(bootstrapDir);

    if (!Files.exists(root)) {
      // Try resolving relative to current working directory or upward
      Path current = Paths.get("").toAbsolutePath();
      LOG.info("Bootstrap directory {} not found in {}, searching upward...", bootstrapDir, current);

      Path candidate = current;
      while (candidate != null) {
        Path check = candidate.resolve(bootstrapDir);
        LOG.debug("Checking for bootstrap directory at: {}", check);
        if (Files.exists(check)) {
          root = check;
          LOG.info("Found bootstrap directory at: {}", root);
          break;
        }
        candidate = candidate.getParent();
      }
    }

    bootstrapInfraConfigs();

    if (!Files.exists(root)) {
      LOG.warn("Bootstrap directory does not exist: {}", bootstrapDir);
      return;
    }

    bootstrapAgents(root.resolve("agents"));
    bootstrapModels(root.resolve("models"));
    LOG.info("Data bootstrapping completed.");
  }

  private void bootstrapInfraConfigs() {
    LOG.info("Bootstrapping infrastructure configurations...");
    DefaultModelConfig defaultModelConfig = infraMongoRepository.findOneByType(DefaultModelConfig.TYPE);
    if (defaultModelConfig == null) {
      LOG.info("Inserting default DefaultModelConfig...");
      defaultModelConfig = new DefaultModelConfig();
      defaultModelConfig.setTitleModelId("qwen2.5-1.5b-instruct-q5_k_m");
      infraMongoRepository.save(defaultModelConfig);
    } else {
      LOG.info("DefaultModelConfig already exists, skipping.");
    }
  }

  private void bootstrapAgents(Path path) {
    if (!Files.exists(path))
      return;
    LOG.info("Bootstrapping agents from: {}", path);
    try (var stream = Files.list(path)) {
      stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
        LOG.info("Found agent config file: {}", p);
        try {
          BaseAgentConfig config = JsonUtils.fromFile(p, BaseAgentConfig.class);
          if (config.getId() == null) {
            config.setId(p.getFileName().toString().replace(".json", ""));
          }
          agentService.saveAgent(config);
          LOG.info("Bootstrapped agent: {}", config.getId());

        } catch (Exception e) {
          LOG.error("Failed to bootstrap agent from file: {}", p, e);
        }
      });
    } catch (Exception e) {
      LOG.error("Failed to list agent bootstrap directory: {}", path, e);
    }
  }

  private void bootstrapModels(Path path) {
    if (!Files.exists(path))
      return;
    LOG.info("Bootstrapping models from: {}", path);
    try (var stream = Files.list(path)) {
      stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
        try {
          ModelConfig config = JsonUtils.fromFile(p, ModelConfig.class);
          if (config.getId() == null) {
            config.setId(p.getFileName().toString().replace(".json", ""));
          }
          modelService.saveModel(config);
          LOG.info("Bootstrapped model: {}", config.getId());
        } catch (Exception e) {
          LOG.error("Failed to bootstrap model from file: {}", p, e);
        }
      });
    } catch (Exception e) {
      LOG.error("Failed to list model bootstrap directory: {}", path, e);
    }
  }
}
