package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mongodb.client.MongoClient;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import org.junit.jupiter.api.Test;

class AgentRepositoryTest {

  @Test
  void findByIdReturnsEmptyForBlankIds() {
    MongoClient mongoClient = mock(MongoClient.class);
    MongoClientSupport mongoClientSupport = mock(MongoClientSupport.class);
    AgentRepository repository = new AgentRepository(mongoClientSupport);

    assertThat(repository.findById(" ")).isEmpty();
    assertThat(repository.findById("")).isEmpty();
  }
}
