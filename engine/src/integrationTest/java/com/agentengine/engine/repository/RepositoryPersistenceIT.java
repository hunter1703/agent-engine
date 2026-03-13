package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.testing.MongoRedisTestResource;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(MongoRedisTestResource.class)
class RepositoryPersistenceIT {

  @Inject
  AgentRepository agentRepository;

  @Inject
  ModelRepository modelRepository;

  @Inject
  MongoClientFactory mongoClientFactory;

  @BeforeEach
  void shouldResetDatabaseWhenTestStarts() {
    try (MongoClient client = mongoClientFactory.getClient()) {
      client.getDatabase("AGENT_ENGINE").drop();
    }
  }

  @Test
  void shouldPersistAndQueryAgentConfigWhenUsingAgentRepository() {
    final DefaultAgentConfig agent = new DefaultAgentConfig();
    agent.setName("Support Agent");
    agent.setModelId("model-1");

    final DefaultAgentConfig inserted = (DefaultAgentConfig) agentRepository.insert(agent);

    assertThat(inserted.getId()).isNotBlank();
    assertThat(agentRepository.findById(inserted.getId())).isPresent();

    final Query query = new Query().withPage(new Page(0, 10));
    query.setIncludeCount(true);
    final PaginatedResult<com.agentengine.engine.api.beans.config.BaseAgentConfig> result = agentRepository.findByQuery(query);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getTotal()).isEqualTo(1L);

    assertThat(agentRepository.deleteById(inserted.getId())).isTrue();
    assertThat(agentRepository.findById(inserted.getId())).isEmpty();
  }

  @Test
  void shouldPreserveGeneratedServerConfigWhenUpdatingOpenAiCompatibleModel() {
    final ModelConfig initial = new ModelConfig();
    initial.setName("OpenAI Compatible Model");
    initial.setType("OPEN_AI_COMPATIBLE");
    initial.setModel("/models/model.gguf");

    final ModelConfig created = modelRepository.insert(initial);

    assertThat(created.getBaseUrl()).isNotBlank();
    assertThat(created.getServerCommand()).isNotBlank();
    assertThat(created.getServerArgs()).isNotEmpty();

    final ModelConfig update = new ModelConfig();
    update.setName("OpenAI Compatible Model");
    update.setType("OPEN_AI_COMPATIBLE");
    update.setModel("/models/model.gguf");

    final ModelConfig saved = modelRepository.update(created.getId(), update);

    assertThat(saved.getBaseUrl()).isEqualTo(created.getBaseUrl());
    assertThat(saved.getServerCommand()).isEqualTo(created.getServerCommand());
    assertThat(saved.getServerArgs()).isEqualTo(created.getServerArgs());
  }
}
