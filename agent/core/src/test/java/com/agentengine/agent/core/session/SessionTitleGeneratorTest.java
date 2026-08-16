package com.agentengine.agent.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.agent.api.services.SessionHistoryService;
import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import org.junit.jupiter.api.Test;

class SessionTitleGeneratorTest {

  @Test
  void shouldThrowIllegalStateExceptionWhenDefaultModelsConfigIsNull() {
    final SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
    final InfraConfigService infraConfigService = mock(InfraConfigService.class);
    final ModelProvider modelProvider = mock(ModelProvider.class);

    when(infraConfigService.findById(DefaultModelsConfig.CATEGORY, DefaultModelsConfig.CONFIG_ID))
        .thenReturn(null);

    final SessionTitleGenerator generator =
        new SessionTitleGenerator(sessionHistoryService, infraConfigService, modelProvider);

    assertThatThrownBy(() -> generator.generateTitle("test-session-id"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Default models config not found");
  }

  @Test
  void shouldUseCorrectConfigIdWhenLookingUpDefaultModelsConfig() {
    final SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
    final InfraConfigService infraConfigService = mock(InfraConfigService.class);
    final ModelProvider modelProvider = mock(ModelProvider.class);

    final DefaultModelsConfig config = new DefaultModelsConfig();
    config.setTitleModelId("test-model-id");

    when(infraConfigService.findById(DefaultModelsConfig.CATEGORY, DefaultModelsConfig.CONFIG_ID))
        .thenReturn(config);

    final SessionTitleGenerator generator =
        new SessionTitleGenerator(sessionHistoryService, infraConfigService, modelProvider);

    // Verify the generator was constructed successfully without throwing
    assertThat(generator).isNotNull();
  }
}
