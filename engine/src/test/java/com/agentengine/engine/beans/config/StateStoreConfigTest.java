package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StateStoreConfigTest {

  @Test
  void stateStoreConfigsSetDefaultTypes() {
    MemoryStateStoreConfig memory = new MemoryStateStoreConfig();
    MongoStateStoreConfig mongo = new MongoStateStoreConfig();

    assertThat(memory.getType()).isEqualTo("memory");
    assertThat(mongo.getType()).isEqualTo("mongo");
  }

  @Test
  void mongoConfigStoresFields() {
    MongoStateStoreConfig mongo = new MongoStateStoreConfig();
    mongo.setUri("mongodb://localhost");
    mongo.setDatabase("db");
    mongo.setSessionsCollection("sessions");
    mongo.setMessagesCollection("messages");
    mongo.setToolExecsCollection("tools");
    mongo.setSummariesCollection("summaries");

    assertThat(mongo.getUri()).isEqualTo("mongodb://localhost");
    assertThat(mongo.getDatabase()).isEqualTo("db");
    assertThat(mongo.getSessionsCollection()).isEqualTo("sessions");
    assertThat(mongo.getMessagesCollection()).isEqualTo("messages");
    assertThat(mongo.getToolExecsCollection()).isEqualTo("tools");
    assertThat(mongo.getSummariesCollection()).isEqualTo("summaries");
  }
}

