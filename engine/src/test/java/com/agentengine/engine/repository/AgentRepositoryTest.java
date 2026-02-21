package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

class AgentRepositoryTest {

  @Test
  @SuppressWarnings("unchecked")
  void findByIdReturnsEmptyForBlankIds() {
    MongoClient mongoClient = mock(MongoClient.class);
    MongoDatabase mongoDatabase = mock(MongoDatabase.class);
    MongoCollection mongoCollection = mock(MongoCollection.class);
    FindIterable findIterable = mock(FindIterable.class);

    when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);
    when(mongoDatabase.getCollection(anyString(), any(Class.class))).thenReturn(mongoCollection);
    when(mongoCollection.find(any(Bson.class))).thenReturn(findIterable);
    when(findIterable.first()).thenReturn(null);

    AgentRepository repository = new AgentRepository(mongoClient);

    assertThat(repository.findById(" ")).isEmpty();
    assertThat(repository.findById("")).isEmpty();
  }
}
