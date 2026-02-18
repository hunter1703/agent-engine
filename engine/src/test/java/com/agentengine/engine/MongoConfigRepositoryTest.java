package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class AgentRepositoryTest {

  @Test
  @SuppressWarnings("unchecked")
  void findByIdReturnsEmptyForBlankIds() {
    com.mongodb.client.MongoClient mongoClient = mock(com.mongodb.client.MongoClient.class);
    com.mongodb.client.MongoDatabase mongoDatabase = mock(com.mongodb.client.MongoDatabase.class);
    com.mongodb.client.MongoCollection mongoCollection = mock(com.mongodb.client.MongoCollection.class);
    com.mongodb.client.FindIterable findIterable = mock(com.mongodb.client.FindIterable.class);

    when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);
    when(mongoDatabase.getCollection(anyString(), any(Class.class))).thenReturn(mongoCollection);
    when(mongoCollection.find(any(org.bson.conversions.Bson.class))).thenReturn(findIterable);
    when(findIterable.first()).thenReturn(null);

    AgentRepository repository = new AgentRepository(mongoClient);

    assertThat(repository.findById(" ")).isEmpty();
    assertThat(repository.findById("")).isEmpty();
  }
}
