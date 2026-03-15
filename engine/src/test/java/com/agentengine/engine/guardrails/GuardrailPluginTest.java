package com.agentengine.engine.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.GuardrailExecutionMode;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.tools.HumanInTheLoopTool;
import com.agentengine.engine.utils.RunUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.BaseAgentState;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class GuardrailPluginTest {

  @Test
  void shouldAllowThroughInputViolationWhenExecutionModeIsOptimistic() {
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", optimisticInputPolicy()));
    final InvocationContext invocationContext = invocationContext("agent-1", "session-1");
    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(invocationContext);
    final LlmRequest.Builder requestBuilder = LlmRequest.builder().contents(List.of(textContent("please process f040_forbidden payload")));

    final boolean empty = plugin.beforeModelCallback(callbackContext, requestBuilder).isEmpty().blockingGet();

    assertThat(empty).isTrue();
    assertThat(RunUtils.getState(invocationContext).violations()).extracting(violation -> violation.code())
        .contains(GuardrailConstants.Code.INPUT_PATTERN);
  }

  @Test
  void shouldEmitInternalHumanDecisionToolCallForInputEscalation() {
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", synchronousInputEscalationPolicy()));
    final InvocationContext invocationContext = invocationContext("agent-1", "session-input-escalate");
    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(invocationContext);

    final LlmResponse response = plugin
        .beforeModelCallback(callbackContext, LlmRequest.builder().contents(List.of(textContent("please process forbidden-input"))))
        .blockingGet();

    assertThat(response).isNotNull();
    final FunctionCall functionCall = response.content().orElseThrow().parts().orElseThrow().getFirst().functionCall().orElseThrow();
    assertThat(functionCall.name()).contains(HumanInTheLoopTool.TOOL_NAME);
    assertThat(functionCall.args().orElseThrow()).containsEntry("kind", "DECISION").containsEntry("options", List.of("ALLOW", "DISALLOW"));
  }

  @Test
  void shouldEmitInternalHumanDecisionToolCallForOutputEscalation() {
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", synchronousOutputEscalationPolicy()));
    final InvocationContext invocationContext = invocationContext("agent-1", "session-output-escalate");
    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(invocationContext);
    final LlmResponse llmResponse = LlmResponse.builder().content(textContent("forbidden-output")).build();

    final LlmResponse response = plugin.afterModelCallback(callbackContext, llmResponse).blockingGet();

    assertThat(response).isNotNull();
    final FunctionCall functionCall = response.content().orElseThrow().parts().orElseThrow().getFirst().functionCall().orElseThrow();
    assertThat(functionCall.name()).contains(HumanInTheLoopTool.TOOL_NAME);
    assertThat(functionCall.args().orElseThrow()).containsEntry("kind", "DECISION").containsEntry("options", List.of("ALLOW", "DISALLOW"));
  }

  private static GuardrailPolicyFactory.GuardrailPolicy optimisticInputPolicy() {
    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setId("f040-input-opt-rule");
    rule.setStage(GuardrailStage.INPUT.name());
    rule.setAction(GuardrailAction.BLOCK.name());
    rule.setMessage("blocked in sync mode");
    rule.setBlockedPatterns(List.of("f040_forbidden"));
    return new GuardrailPolicyFactory.GuardrailPolicy(true, GuardrailErrorMode.FAIL_OPEN, GuardrailExecutionMode.OPTIMISTIC,
        Map.of(GuardrailStage.INPUT, List.of(new com.agentengine.engine.guardrails.rules.TextContentGuardrail(rule))));
  }

  private static GuardrailPolicyFactory.GuardrailPolicy synchronousInputEscalationPolicy() {
    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setId("input-escalate");
    rule.setStage(GuardrailStage.INPUT.name());
    rule.setAction(GuardrailAction.ESCALATE.name());
    rule.setMessage("Input needs approval.");
    rule.setBlockedPatterns(List.of("forbidden-input"));
    return new GuardrailPolicyFactory.GuardrailPolicy(true, GuardrailErrorMode.FAIL_OPEN, GuardrailExecutionMode.SYNC,
        Map.of(GuardrailStage.INPUT, List.of(new com.agentengine.engine.guardrails.rules.TextContentGuardrail(rule))));
  }

  private static GuardrailPolicyFactory.GuardrailPolicy synchronousOutputEscalationPolicy() {
    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setId("output-escalate");
    rule.setStage(GuardrailStage.OUTPUT.name());
    rule.setAction(GuardrailAction.ESCALATE.name());
    rule.setMessage("Output needs approval.");
    rule.setBlockedPatterns(List.of("forbidden-output"));
    return new GuardrailPolicyFactory.GuardrailPolicy(true, GuardrailErrorMode.FAIL_OPEN, GuardrailExecutionMode.SYNC,
        Map.of(GuardrailStage.OUTPUT, List.of(new com.agentengine.engine.guardrails.rules.TextContentGuardrail(rule))));
  }

  private static InvocationContext invocationContext(final String agentId, final String sessionId) {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final Map<String, BaseAgentState> agentStates = new ConcurrentHashMap<>();
    final Session session = Session.builder(sessionId).appName(agentId).userId("default").state(state).events(new ArrayList<>())
        .lastUpdateTime(Instant.now()).build();
    final BaseAgent invocationAgent = mock(BaseAgent.class);
    when(invocationAgent.name()).thenReturn(agentId);

    final InvocationContext invocationContext = mock(InvocationContext.class);
    when(invocationContext.agent()).thenReturn(invocationAgent);
    when(invocationContext.session()).thenReturn(session);
    when(invocationContext.agentStates()).thenReturn(agentStates);
    return invocationContext;
  }

  private static Tool runtimeTool(final String name, final String description) {
    final Tool tool = mock(Tool.class);
    when(tool.name()).thenReturn(name);
    when(tool.description()).thenReturn(description);
    when(tool.descriptor()).thenReturn(new ToolDescriptor(name, description, Map.of()));
    return tool;
  }

  private static Content textContent(final String text) {
    return Content.builder().role("user").parts(List.of(Part.fromText(text))).build();
  }
}
