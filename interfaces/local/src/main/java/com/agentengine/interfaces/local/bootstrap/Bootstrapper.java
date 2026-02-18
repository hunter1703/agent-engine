package com.agentengine.interfaces.local.bootstrap;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.model.TitleConfig;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.agentengine.engine.repository.ModelRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ApplicationScoped
public class Bootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(Bootstrapper.class);

  @ConfigProperty(name = "agent.engine.bootstrap.dir", defaultValue = "configs")
  String bootstrapDir;

  @Inject
  AgentRepository agentRepository;

  @Inject
  ModelRepository modelRepository;

  @Inject
  InfraMongoRepository infraMongoRepository;

  void onStart(@Observes StartupEvent ev) {
    LOG.info("Starting data bootstrapping from directory: {}", bootstrapDir);
    Path root = Paths.get(bootstrapDir);

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
    TitleConfig titleConfig = infraMongoRepository.findOneByType(TitleConfig.TYPE);
    if (titleConfig == null) {
      LOG.info("Inserting default TitleConfig...");
      titleConfig = new TitleConfig();
      titleConfig.setModelId("qwen2.5-1.5b-instruct-q5_k_m");
      infraMongoRepository.save(titleConfig);
    } else {
      LOG.info("TitleConfig already exists, skipping.");
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
          AgentConfig config = JsonUtils.fromFile(p, AgentConfig.class);
          if (config.getId() == null) {
            config.setId(p.getFileName().toString().replace(".json", ""));
          }
          agentRepository.save(config);
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
          modelRepository.save(config);
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
