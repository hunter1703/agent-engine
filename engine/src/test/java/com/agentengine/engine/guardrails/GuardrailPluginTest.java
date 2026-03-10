package com.agentengine.engine.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.GuardrailExecutionMode;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import com.agentengine.engine.api.beans.config.ToolSafetyGuardrailRule;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.engine.guardrails.rules.TextContentGuardrail;
import com.agentengine.engine.guardrails.rules.ToolSafetyGuardrail;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.SessionStateUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
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
    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setId("f040-input-opt-rule");
    rule.setStage(GuardrailStage.INPUT);
    rule.setAction(GuardrailAction.BLOCK);
    rule.setMessage("blocked in sync mode");
    rule.setBlockedPatterns(List.of("f040_forbidden"));

    final GuardrailPolicyFactory.GuardrailPolicy policy =
        new GuardrailPolicyFactory.GuardrailPolicy(
            true,
            GuardrailErrorMode.FAIL_OPEN,
            GuardrailExecutionMode.OPTIMISTIC,
            Map.of(GuardrailStage.INPUT, List.of(new TextContentGuardrail(rule))));
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", policy));

    final InvocationContext invocationContext = invocationContext("agent-1", "session-1");
    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(invocationContext);
    final LlmRequest.Builder requestBuilder =
        LlmRequest.builder().contents(List.of(textContent("please process f040_forbidden payload")));

    final boolean empty = plugin.beforeModelCallback(callbackContext, requestBuilder).isEmpty().blockingGet();

    assertThat(empty).isTrue();
    assertThat(RunStateUtils.getState(invocationContext).violations())
        .extracting(violation -> violation.code())
        .contains(GuardrailConstants.Code.INPUT_PATTERN);
  }

  @Test
  void shouldAllowThroughToolViolationWhenExecutionModeIsOptimistic() {
    final ToolSafetyGuardrailRule rule = new ToolSafetyGuardrailRule();
    rule.setId("f042-tool-opt-rule");
    rule.setAction(GuardrailAction.BLOCK);
    rule.setMessage("blocked in sync mode");
    rule.setToolNames(List.of("run_cmd"));
    rule.setMinToolRisk(ToolRiskLevel.UNKNOWN);

    final GuardrailPolicyFactory.GuardrailPolicy policy =
        new GuardrailPolicyFactory.GuardrailPolicy(
            true,
            GuardrailErrorMode.FAIL_OPEN,
            GuardrailExecutionMode.OPTIMISTIC,
            Map.of(GuardrailStage.TOOL, List.of(new ToolSafetyGuardrail(rule))));
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", policy));

    final InvocationContext invocationContext = invocationContext("agent-1", "session-2");
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.invocationContext()).thenReturn(invocationContext);

    final BaseTool tool = mock(BaseTool.class);
    when(tool.name()).thenReturn("run_cmd");
    when(tool.description()).thenReturn("run command");

    final boolean empty =
        plugin
            .beforeToolCallback(tool, Map.of("command", "echo F042"), toolContext)
            .isEmpty()
            .blockingGet();

    assertThat(empty).isTrue();
    assertThat(RunStateUtils.getState(invocationContext).violations())
        .extracting(violation -> violation.code())
        .contains(GuardrailConstants.Code.TOOL_POLICY);
  }

  @Test
  void shouldAllowThroughOutputViolationWhenExecutionModeIsOptimistic() {
    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setId("f044-output-opt-rule");
    rule.setStage(GuardrailStage.OUTPUT);
    rule.setAction(GuardrailAction.BLOCK);
    rule.setMessage("blocked in sync mode");
    rule.setBlockedPatterns(List.of("f044_forbidden"));

    final GuardrailPolicyFactory.GuardrailPolicy policy =
        new GuardrailPolicyFactory.GuardrailPolicy(
            true,
            GuardrailErrorMode.FAIL_OPEN,
            GuardrailExecutionMode.OPTIMISTIC,
            Map.of(GuardrailStage.OUTPUT, List.of(new TextContentGuardrail(rule))));
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", policy));

    final InvocationContext invocationContext = invocationContext("agent-1", "session-3");
    when(invocationContext.invocationId()).thenReturn("inv-1");
    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(invocationContext);

    final LlmResponse llmResponse =
        LlmResponse.builder().content(textContent("f044_forbidden")).turnComplete(true).build();

    final boolean afterModelEmpty =
        plugin.afterModelCallback(callbackContext, llmResponse).isEmpty().blockingGet();
    final Event terminalEvent =
        Event.builder().id("event-1").author("agent-1").turnComplete(true).build();
    final boolean onEventEmpty =
        plugin.onEventCallback(invocationContext, terminalEvent).isEmpty().blockingGet();

    assertThat(afterModelEmpty).isTrue();
    assertThat(onEventEmpty).isTrue();
    assertThat(RunStateUtils.getState(invocationContext).violations())
        .extracting(violation -> violation.code())
        .contains(GuardrailConstants.Code.OUTPUT_PATTERN);
  }

  @Test
  void shouldStorePendingToolNameWhenEscalating() {
    final ToolSafetyGuardrailRule rule = new ToolSafetyGuardrailRule();
    rule.setId("f023-escalate-rule");
    rule.setAction(GuardrailAction.ESCALATE);
    rule.setMessage("Tool execution requires confirmation.");
    rule.setToolNames(List.of("run_cmd"));
    rule.setMinToolRisk(ToolRiskLevel.UNKNOWN);

    final GuardrailPolicyFactory.GuardrailPolicy policy =
        new GuardrailPolicyFactory.GuardrailPolicy(
            true,
            GuardrailErrorMode.FAIL_OPEN,
            GuardrailExecutionMode.SYNC,
            Map.of(GuardrailStage.TOOL, List.of(new ToolSafetyGuardrail(rule))));
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", policy));

    final InvocationContext invocationContext = invocationContext("agent-1", "session-escalate");
    when(invocationContext.invocationId()).thenReturn("inv-escalate");

    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.invocationContext()).thenReturn(invocationContext);
    when(toolContext.functionCallId()).thenReturn(Optional.of("call-123"));

    final BaseTool tool = mock(BaseTool.class);
    when(tool.name()).thenReturn("run_cmd");
    when(tool.description()).thenReturn("run command");

    plugin.beforeToolCallback(tool, Map.of("command", "echo hello"), toolContext).blockingGet();

    assertThat(SessionStateUtils.getPendingToolName(invocationContext)).isEqualTo("run_cmd");
  }

  @Test
  void shouldBypassEscalationForPreviouslyApprovedTool() {
    final ToolSafetyGuardrailRule rule = new ToolSafetyGuardrailRule();
    rule.setId("f023-approved-bypass-rule");
    rule.setAction(GuardrailAction.ESCALATE);
    rule.setMessage("Tool execution requires confirmation.");
    rule.setToolNames(List.of("run_cmd"));
    rule.setMinToolRisk(ToolRiskLevel.UNKNOWN);

    final GuardrailPolicyFactory.GuardrailPolicy policy =
        new GuardrailPolicyFactory.GuardrailPolicy(
            true,
            GuardrailErrorMode.FAIL_OPEN,
            GuardrailExecutionMode.SYNC,
            Map.of(GuardrailStage.TOOL, List.of(new ToolSafetyGuardrail(rule))));
    final GuardrailPlugin plugin = new GuardrailPlugin(Map.of("agent-1", policy));

    final InvocationContext invocationContext = invocationContext("agent-1", "session-approved");
    SessionStateUtils.markToolApproved(invocationContext, "run_cmd");

    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.invocationContext()).thenReturn(invocationContext);
    when(toolContext.functionCallId()).thenReturn(Optional.of("call-123"));

    final BaseTool tool = mock(BaseTool.class);
    when(tool.name()).thenReturn("run_cmd");
    when(tool.description()).thenReturn("run command");

    final boolean empty =
        plugin.beforeToolCallback(tool, Map.of("command", "echo approved"), toolContext).isEmpty().blockingGet();

    assertThat(empty).isTrue();
    assertThat(SessionStateUtils.isPaused(invocationContext)).isFalse();
    assertThat(SessionStateUtils.getState(invocationContext).getApprovedToolName()).isNull();
  }

  private static InvocationContext invocationContext(final String agentId, final String sessionId) {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final Session session =
        Session.builder(sessionId)
            .appName(agentId)
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    final BaseAgent invocationAgent = mock(BaseAgent.class);
    when(invocationAgent.name()).thenReturn(agentId);

    final InvocationContext invocationContext = mock(InvocationContext.class);
    when(invocationContext.agent()).thenReturn(invocationAgent);
    when(invocationContext.session()).thenReturn(session);
    return invocationContext;
  }

  private static Content textContent(final String text) {
    return Content.builder().role("user").parts(List.of(Part.fromText(text))).build();
  }
}
