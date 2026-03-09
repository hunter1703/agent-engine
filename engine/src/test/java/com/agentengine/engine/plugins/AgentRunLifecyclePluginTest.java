package com.agentengine.engine.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class AgentRunLifecyclePluginTest {

  @Test
  void shouldInitializeAndClearRunState() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final Session session =
        Session.builder("session-1")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    final BaseAgent invocationAgent = mock(BaseAgent.class);
    when(invocationAgent.name()).thenReturn("agent-1");

    final InvocationContext invocationContext = mock(InvocationContext.class);
    when(invocationContext.agent()).thenReturn(invocationAgent);
    when(invocationContext.session()).thenReturn(session);

    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(invocationContext);

    final AgentRunLifecyclePlugin plugin = new AgentRunLifecyclePlugin();
    plugin.beforeAgentCallback(mock(BaseAgent.class), callbackContext).blockingGet();
    assertThat(state).containsKey("run.state.agent-1");

    plugin.afterAgentCallback(mock(BaseAgent.class), callbackContext).blockingGet();
    assertThat(state).doesNotContainKey("run.state.agent-1");
  }
}
