package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.LastNContextManagerConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.beans.config.MongoStateStoreConfig;
import org.junit.jupiter.api.Test;

class ConfigBeansTest {

  @Test
  void mongoStateStoreConfigStoresValues() {
    MongoStateStoreConfig config = new MongoStateStoreConfig();
    config.setUri("mongodb://localhost");
    config.setDatabase("db");
    config.setSessionsCollection("sessions");
    config.setMessagesCollection("messages");
    config.setToolExecsCollection("tool_execs");
    config.setSummariesCollection("summaries");

    assertThat(config.getType()).isEqualTo("mongo");
    assertThat(config.getUri()).isEqualTo("mongodb://localhost");
    assertThat(config.getDatabase()).isEqualTo("db");
    assertThat(config.getSessionsCollection()).isEqualTo("sessions");
    assertThat(config.getMessagesCollection()).isEqualTo("messages");
    assertThat(config.getToolExecsCollection()).isEqualTo("tool_execs");
    assertThat(config.getSummariesCollection()).isEqualTo("summaries");
  }

  @Test
  void lastNContextConfigStoresValues() {
    LastNContextManagerConfig config = new LastNContextManagerConfig();
    config.setKeepLast(5);
    config.setSystemPrompt("system");

    assertThat(config.getType()).isEqualTo("last_n");
    assertThat(config.getKeepLast()).isEqualTo(5);
    assertThat(config.getSystemPrompt()).isEqualTo("system");
  }

  @Test
  void modelConfigStoresValues() {
    ModelConfig config = new ModelConfig();
    config.setBaseUrl("http://localhost");
    config.setType("OPEN_AI");
    config.setModel("gpt-4");
    config.setTemperature(0.1);
    config.setTopK(4);
    config.setTopP(0.9);
    config.setRepeatPenalty(1.1);
    config.setNumPredict(128);
    config.setMaxContextLength(8192);
    config.setResponseFormat("text");

    assertThat(config.getBaseUrl()).isEqualTo("http://localhost");
    assertThat(config.getType()).isEqualTo("OPEN_AI");
    assertThat(config.getModel()).isEqualTo("gpt-4");
    assertThat(config.getTemperature()).isEqualTo(0.1);
    assertThat(config.getTopK()).isEqualTo(4);
    assertThat(config.getTopP()).isEqualTo(0.9);
    assertThat(config.getRepeatPenalty()).isEqualTo(1.1);
    assertThat(config.getNumPredict()).isEqualTo(128);
    assertThat(config.getMaxContextLength()).isEqualTo(8192);
    assertThat(config.getResponseFormat()).isEqualTo("text");
    assertThat(config.getContextConfig()).isInstanceOf(LastNContextManagerConfig.class);
  }
}
