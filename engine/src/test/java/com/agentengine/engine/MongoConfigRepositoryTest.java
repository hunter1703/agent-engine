package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.api.beans.config.LastNContextConfig;
import com.agentengine.engine.api.beans.config.MemoryStateStoreConfig;
import com.agentengine.engine.api.beans.config.MongoStateStoreConfig;
import com.agentengine.engine.api.beans.config.RouterEngineConfig;
import com.agentengine.engine.api.beans.config.SummarizingContextConfig;
import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.junit.jupiter.api.Test;

import java.util.List;

class MongoConfigRepositoryTest {

  @Test
  void buildClientSettingsRegistersPojoCodecs() {
    List<String> discriminators = List.of(HybridAgentConfig.class.getName(), RouterEngineConfig.class.getName(),
        LastNContextConfig.class.getName(), SummarizingContextConfig.class.getName(),
        MemoryStateStoreConfig.class.getName(), MongoStateStoreConfig.class.getName());
    MongoClientSettings settings = MongoConfigRepository.buildClientSettings("mongodb://localhost:27000",
        discriminators);

    Codec<AgentConfig> codec = settings.getCodecRegistry().get(AgentConfig.class);
    assertThat(codec).isNotNull();

    String json = "{" + "\"_id\":\"shell_agent\","
        + "\"engine\":{\"type\":\"hybrid\",\"systemPrompt\":\"You are a helper.\",\"reasoningModelId\":\"model\",\"toolAssistantModelId\":\"model\"},"
        + "\"context\":{\"type\":\"last_n\",\"keepLast\":10}," + "\"stateStore\":{\"type\":\"memory\"}" + "}";
    BsonDocument document = BsonDocument.parse(json);
    AgentConfig config = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

    assertThat(config).isNotNull();
    assertThat(config.getEngine().getType()).isEqualTo("hybrid");
  }
}
