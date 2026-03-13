package com.agentengine.engine.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.processors.request.CorrectionProcessor;
import com.agentengine.engine.agents.processors.request.PlanningRequestProcessor;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.AgentTransfer;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.BaseLlm;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaseAgentTest {

  @Test
  void shouldUseSingleFlowProcessorsWhenTransfersAreDisabledAndNoSubAgentsExist() {
    final BaseAgent agent = buildAgent(true, true, List.of());

    final List<RequestProcessor> requestProcessors = requestProcessors(agent);

    assertThat(requestProcessors).contains(CorrectionProcessor.INSTANCE, PlanningRequestProcessor.INSTANCE);
    assertThat(requestProcessors).anyMatch(AgentTransfer.class::isInstance);
  }

  @Test
  void shouldUseAutoFlowProcessorsWhenTransfersAreEnabledWithoutSubAgents() {
    final BaseAgent agent = buildAgent(false, false, List.of());

    assertThat(requestProcessors(agent)).anyMatch(AgentTransfer.class::isInstance);
  }

  @Test
  void shouldUseAutoFlowProcessorsWhenSubAgentsExist() {
    final com.google.adk.agents.BaseAgent subAgent = mock(com.google.adk.agents.BaseAgent.class);
    when(subAgent.name()).thenReturn("sub-agent");
    final BaseAgent agent = buildAgent(true, true, List.of(subAgent));

    assertThat(requestProcessors(agent)).anyMatch(AgentTransfer.class::isInstance);
  }

  private static BaseAgent buildAgent(final boolean disallowTransferToParent, final boolean disallowTransferToPeers,
      final List<? extends com.google.adk.agents.BaseAgent> subAgents) {
    final BaseLlm model = mock(BaseLlm.class);
    when(model.model()).thenReturn("mock-model");
    final LlmAgent.Builder builder = LlmAgent.builder().name("agent").model(model).disallowTransferToParent(disallowTransferToParent)
        .disallowTransferToPeers(disallowTransferToPeers).subAgents(subAgents);
    return new BaseAgent(builder);
  }

  @SuppressWarnings("unchecked")
  private static List<RequestProcessor> requestProcessors(final BaseAgent agent) {
    try {
      final Field flowField = LlmAgent.class.getDeclaredField("llmFlow");
      flowField.setAccessible(true);
      final BaseLlmFlow flow = (BaseLlmFlow) flowField.get(agent);
      final Field requestProcessorsField = BaseLlmFlow.class.getDeclaredField("requestProcessors");
      requestProcessorsField.setAccessible(true);
      return (List<RequestProcessor>) requestProcessorsField.get(flow);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Failed to inspect resolved LLM flow.", ex);
    }
  }
}
