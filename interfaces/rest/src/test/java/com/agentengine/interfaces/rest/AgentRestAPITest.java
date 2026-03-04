package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamAguiEventsRequestHandler;
import com.agentengine.interfaces.rest.support.HandlerInstance;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class AgentRestAPITest {

  @Test
  void createAgentStoresConfig() {
    final AgentService agentService = mock(AgentService.class);
    final AgentConfig config = buildValidAgentConfig();
    when(agentService.createAgent(config)).thenReturn(config);

    final AgentRestAPI resource =
        new AgentRestAPI(buildHandlers(mock(AgentExecutionService.class)), agentService, mock(com.agentengine.engine.api.services.SessionService.class));

    final AgentConfig response = resource.createAgent(config);

    assertThat(response).isInstanceOf(AgentConfig.class);
    final AgentConfig created = response;
    assertThat(created.getId()).isEqualTo("agent");
    verify(agentService).createAgent(config);
  }

  @Test
  void createAgentGeneratesIdWhenMissing() {
    final AgentService agentService = mock(AgentService.class);
    final AgentConfig config = buildValidAgentConfig();
    config.setId(null);

    // Simulate ID generation
    doAnswer(assignId("generated-id")).when(agentService).createAgent(any(AgentConfig.class));

    final AgentRestAPI resource =
        new AgentRestAPI(buildHandlers(mock(AgentExecutionService.class)), agentService, mock(com.agentengine.engine.api.services.SessionService.class));

    final AgentConfig response = resource.createAgent(config);

    assertThat(response.getId()).isEqualTo("generated-id");
  }

  private static Instance<AgentRequestHandler<?>> buildHandlers(
      final AgentExecutionService executionService) {
    final StreamAguiEventsRequestHandler streamingHandler =
        new StreamAguiEventsRequestHandler(executionService);
    return new HandlerInstance(List.of(streamingHandler));
  }

  private static AgentConfig buildValidAgentConfig() {
    final AgentConfig config = new AgentConfig();
    config.setId("agent");
    final AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("model");
    modelConfig.setSystemPrompt("system");
    config.setModel(modelConfig);
    return config;
  }

  private static Answer<AgentConfig> assignId(final String id) {
    return invocation -> {
      final AgentConfig config = invocation.getArgument(0);
      if (config.getId() == null) {
        config.setId(id);
      }
      return config;
    };
  }
}
