package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ModelConfig;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class MongoConfigRepositoryTest {

  @Test
  void mapDocumentParsesModelConfig() {
    Document document = new Document();
    document.put("_id", "qwen2_5_0_5b_instruct");
    document.put("provider", "OLLAMA");
    document.put("model", "qwen2.5-0.5b");

    ModelConfig config = MongoConfigRepository.mapDocument(document, ModelConfig.class);

    assertThat(config).isNotNull();
    assertThat(config.getProvider()).isEqualTo("OLLAMA");
    assertThat(config.getModel()).isEqualTo("qwen2.5-0.5b");
  }

  @Test
  void mapDocumentParsesAgentConfig() {
    Document document = new Document();
    document.put("_id", "shell_agent");
    document.put("engine", new Document("type", "hybrid")
        .append("systemPrompt", "You are a helper.")
        .append("reasoning", "qwen2_5_0_5b_instruct")
        .append("tool", "qwen2_5_0_5b_instruct"));

    AgentConfig config = MongoConfigRepository.mapDocument(document, AgentConfig.class);

    assertThat(config).isNotNull();
    assertThat(config.getEngine().getType()).isEqualTo("hybrid");
  }
}
