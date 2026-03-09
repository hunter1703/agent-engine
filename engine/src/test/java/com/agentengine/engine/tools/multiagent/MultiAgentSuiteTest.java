package com.agentengine.engine.tools.multiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.google.adk.events.Event;
import com.google.adk.sessions.InMemorySessionService;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiAgentSuiteTest {

  @Test
  void exposesSuiteAndToolDescriptors() {
    final AgentExecutionService noopService = request -> Flowable.<Event>empty();
    final MultiAgentSuite suite = new MultiAgentSuite(noopService);

    assertThat(suite.descriptor().name()).isEqualTo("multi_agent");
    assertThat(suite.tools()).extracting(descriptor -> descriptor.name())
        .contains("multi_agent", "delegate_to_agent", "handoff_to_agent", "parallel_delegate");
    assertThat(suite.toolNames())
        .containsExactlyInAnyOrder("delegate_to_agent", "handoff_to_agent", "parallel_delegate");
  }

  @Test
  void createsToolsByName() {
    final AgentExecutionService noopService = request -> Flowable.<Event>empty();
    final MultiAgentSuite suite = new MultiAgentSuite(noopService);
    final AgentConfig config = new AgentConfig();
    config.setId("manager");
    final AgentContext context = new AgentContext(config, new InMemorySessionService());

    assertThat(suite.create(context, "delegate_to_agent", Map.of())).isNotNull();
    assertThat(suite.create(context, "handoff_to_agent", Map.of())).isNotNull();
    assertThat(suite.create(context, "parallel_delegate", Map.of())).isNotNull();
    assertThat(suite.create(context, "unknown", Map.of())).isNull();
  }
}
