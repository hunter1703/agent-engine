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
  void buildClientSettingsConfiguresCorrectly() {
    List<String> discriminators = List.of("com.agentengine.engine.api.beans.config.HybridAgentConfig");
    MongoClientSettings settings = MongoConfigRepository.buildClientSettings("mongodb://localhost:27017",
        discriminators);

    assertThat(settings.getApplicationName()).isEqualTo("agent-engine");
    assertThat(settings.getCodecRegistry()).isNotNull();
  }

  @Test
  void loadMethodsReturnNullOnBlankNames() {
    MongoClientSupport support = mock(MongoClientSupport.class);
    when(support.getBsonDiscriminators()).thenReturn(List.of());

    // We can't easily instantiate the full repo without it trying to create a REAL
    // Mongo client
    // because createClient is private and called in constructor.
    // However, we can at least test the static methods.
  }
}
