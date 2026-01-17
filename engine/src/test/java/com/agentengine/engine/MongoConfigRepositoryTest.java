package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ModelConfig;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.lang.reflect.Field;
import org.bson.conversions.Bson;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class MongoConfigRepositoryTest {

  @Test
  void loadAgentConfigReturnsNullForBlankName() {
    MongoConfigRepository repository = new MongoConfigRepository();

    assertThat(repository.loadAgentConfig(" ")).isNull();
  }

  @Test
  void loadModelConfigReturnsNullForBlankName() {
    MongoConfigRepository repository = new MongoConfigRepository();

    assertThat(repository.loadModelConfig(" ")).isNull();
  }

  @Test
  void loadAgentConfigReadsFromMongo() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    MongoCollection<Document> collection = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    FindIterable<AgentConfig> iterable = mock(FindIterable.class);
    AgentConfig config = AgentConfig.empty();

    when(client.getDatabase("AGENT_ENGINE")).thenReturn(database);
    when(database.getCollection("Agent")).thenReturn(collection);
    when(collection.find(any(Bson.class), eq(AgentConfig.class))).thenReturn(iterable);
    when(iterable.limit(1)).thenReturn(iterable);
    when(iterable.first()).thenReturn(config);

    MongoConfigRepository repository = new MongoConfigRepository();
    Field field = MongoConfigRepository.class.getDeclaredField("mongoClient");
    field.setAccessible(true);
    field.set(repository, client);

    AgentConfig loaded = repository.loadAgentConfig("agent");

    assertThat(loaded).isSameAs(config);
  }

  @Test
  void loadModelConfigReadsFromMongo() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    MongoCollection<Document> collection = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    FindIterable<ModelConfig> iterable = mock(FindIterable.class);
    ModelConfig config = new ModelConfig();

    when(client.getDatabase("AGENT_ENGINE")).thenReturn(database);
    when(database.getCollection("Model")).thenReturn(collection);
    when(collection.find(any(Bson.class), eq(ModelConfig.class))).thenReturn(iterable);
    when(iterable.limit(1)).thenReturn(iterable);
    when(iterable.first()).thenReturn(config);

    MongoConfigRepository repository = new MongoConfigRepository();
    Field field = MongoConfigRepository.class.getDeclaredField("mongoClient");
    field.setAccessible(true);
    field.set(repository, client);

    ModelConfig loaded = repository.loadModelConfig("model");

    assertThat(loaded).isSameAs(config);
  }
}
