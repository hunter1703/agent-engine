package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.MongoClientSettings;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

class MongoConfigRepositoryTest {

  @Test
  void buildClientSettingsIgnoresUnknownDiscriminator() {
    MongoClientSettings settings = MongoConfigRepository.buildClientSettings(
        "mongodb://localhost:27017", List.of("com.agentengine.MissingType"));

    assertThat(settings.getApplicationName()).isEqualTo("agent-engine");
    assertThat(settings.getCodecRegistry()).isNotNull();
  }

  @Test
  void loadConfigReturnsNullForBlankNames() {
    MongoClientSupport mongoClientSupport = mock(MongoClientSupport.class);
    when(mongoClientSupport.getBsonDiscriminators()).thenReturn(List.of());
    MongoConfigRepository repository = new MongoConfigRepository(mongoClientSupport);

    assertThat(repository.loadAgentConfig(" ")).isNull();
    assertThat(repository.loadModelConfig("")).isNull();
  }
}
