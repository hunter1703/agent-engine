package com.agentengine.engine.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.google.adk.agents.LlmAgent;
import com.google.adk.sessions.BaseSessionService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentSessionRuntimeManagerTest {

  @Test
  void usesSessionServiceFromProviderWithoutDecoration() {
    final AgentRepository agentRepository = mock(AgentRepository.class);
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final AgentSessionRepository agentSessionRepository = mock(AgentSessionRepository.class);

    final AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("model-id");
    modelConfig.setSystemPrompt("prompt");

    final AgentConfig agentConfig = new AgentConfig();
    agentConfig.setId("agent-id");
    agentConfig.setModel(modelConfig);

    when(agentRepository.findById("agent-id")).thenReturn(Optional.of(agentConfig));
    when(agentSessionRepository.findById("session-id"))
        .thenReturn(Optional.of(new AgentSession("session-id", "agent-id", "title")));

    final BaseSessionService sessionService = mock(BaseSessionService.class);
    when(sessionServiceProvider.get(any())).thenReturn(sessionService);

    final LlmAgent agent = mock(LlmAgent.class);
    when(agentProvider.get(any(), any())).thenReturn(agent);

    final AgentSessionRuntimeManager manager = new AgentSessionRuntimeManager(agentRepository, agentProvider,
        sessionServiceProvider, agentSessionRepository);

    final AgentSessionRuntime runtime = manager.getOrStartRuntime("agent-id", "session-id");

    assertThat(runtime.runner().sessionService()).isSameAs(sessionService);
  }
}
