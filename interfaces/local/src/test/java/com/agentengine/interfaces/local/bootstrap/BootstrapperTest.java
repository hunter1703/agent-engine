package com.agentengine.interfaces.local.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.util.mongodb.infra.DefaultModelConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class BootstrapperTest {

  @TempDir Path tempDir;

  @Test
  void shouldBootstrapInfraAgentsAndModelsWhenBootstrapDirectoryExists() throws Exception {
    final Path bootstrapDir = tempDir.resolve("configs");
    final Path agentsDir = bootstrapDir.resolve("agents");
    final Path modelsDir = bootstrapDir.resolve("models");
    Files.createDirectories(agentsDir);
    Files.createDirectories(modelsDir);

    Files.writeString(
        agentsDir.resolve("agent_one.json"),
        """
        {
          "type": "default",
          "name": "Agent One",
          "modelId": "model_one"
        }
        """);
    Files.writeString(
        modelsDir.resolve("model_one.json"),
        """
        {
          "type": "ollama",
          "name": "Model One",
          "model": "qwen2.5"
        }
        """);

    final AgentService agentService = Mockito.mock(AgentService.class);
    final ModelService modelService = Mockito.mock(ModelService.class);
    final InfraMongoRepository infraMongoRepository = Mockito.mock(InfraMongoRepository.class);
    when(infraMongoRepository.findOneByType(DefaultModelConfig.TYPE)).thenReturn(null);

    final Bootstrapper bootstrapper = new Bootstrapper();
    bootstrapper.bootstrapDir = bootstrapDir.toString();
    bootstrapper.agentService = agentService;
    bootstrapper.modelService = modelService;
    bootstrapper.infraMongoRepository = infraMongoRepository;

    bootstrapper.onStart(null);

    verify(infraMongoRepository).findOneByType(DefaultModelConfig.TYPE);
    verify(infraMongoRepository).save(any(DefaultModelConfig.class));
    verify(agentService, times(1)).saveAgent(any());
    verify(modelService, times(1)).saveModel(any());
  }

  @Test
  void shouldSkipAgentsAndModelsWhenBootstrapDirectoryMissing() {
    final AgentService agentService = Mockito.mock(AgentService.class);
    final ModelService modelService = Mockito.mock(ModelService.class);
    final InfraMongoRepository infraMongoRepository = Mockito.mock(InfraMongoRepository.class);
    when(infraMongoRepository.findOneByType(DefaultModelConfig.TYPE))
        .thenReturn(new DefaultModelConfig());

    final Bootstrapper bootstrapper = new Bootstrapper();
    bootstrapper.bootstrapDir = tempDir.resolve("missing-configs").toString();
    bootstrapper.agentService = agentService;
    bootstrapper.modelService = modelService;
    bootstrapper.infraMongoRepository = infraMongoRepository;

    bootstrapper.onStart(null);

    verify(infraMongoRepository).findOneByType(DefaultModelConfig.TYPE);
    verify(infraMongoRepository, never()).save(any(DefaultModelConfig.class));
    verify(agentService, never()).saveAgent(any());
    verify(modelService, never()).saveModel(any());
  }
}
