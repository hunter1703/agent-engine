package com.agentengine.engine.builders.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.SummarizeContextManagerConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.context.SessionContextSummaryService;
import com.agentengine.engine.context.SummarizingContextManager;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.google.adk.models.BaseLlm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SummarizeContextManagerBuilderTest {

  @Test
  void fallsBackToAgentModelWhenNoSummaryModelConfigured() {
    final ModelProvider modelProvider = mock(ModelProvider.class);
    final InfraMongoRepository infraMongoRepository = mock(InfraMongoRepository.class);
    final SessionContextSummaryService summaryService = mock(SessionContextSummaryService.class);
    final BaseLlm summaryModel = mock(BaseLlm.class);
    when(modelProvider.get(any(AgentModelConfig.class))).thenReturn(summaryModel);

    final SummarizeContextManagerBuilder builder =
        new SummarizeContextManagerBuilder(modelProvider, infraMongoRepository, summaryService);
    final SummarizeContextManagerConfig contextConfig = new SummarizeContextManagerConfig();
    contextConfig.setTriggerTokens(256);
    contextConfig.setRecencyTokens(64);

    final SummarizingContextManager contextManager = builder.build(contextConfig, "agent-model-id");

    assertThat(contextManager).isNotNull();
    final ArgumentCaptor<AgentModelConfig> captor = ArgumentCaptor.forClass(AgentModelConfig.class);
    verify(modelProvider).get(captor.capture());
    assertThat(captor.getValue().getModelId()).isEqualTo("agent-model-id");
  }
}
