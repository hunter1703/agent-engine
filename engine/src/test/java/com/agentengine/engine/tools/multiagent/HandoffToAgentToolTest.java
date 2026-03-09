package com.agentengine.engine.tools.multiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class HandoffToAgentToolTest {

  @Test
  void recordsHandoffMetadataInSessionState() {
    final AgentConfig config = new AgentConfig();
    config.setId("triage-agent");
    final AgentContext agentContext = new AgentContext(config, new InMemorySessionService());
    final MultiAgentTelemetry telemetry = new MultiAgentTelemetry();
    final HandoffToAgentTool tool =
        new HandoffToAgentTool(
            agentContext,
            new MultiAgentToolConfig(java.util.List.of(), false, 8000, true),
            telemetry);

    final Session session =
        Session.builder("session-1")
            .appName("triage-agent")
            .userId("default")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext invocationContext = InvocationContext.builder().session(session).build();
    final ToolContext toolContext = ToolContext.builder(invocationContext).build();

    final Map<String, Object> result =
        tool.execute(toolContext, "support-agent", "Needs specialist", "Please investigate logs");

    assertThat(result).containsEntry("status", "handoff_requested");
    assertThat(result).containsEntry("target_agent_id", "support-agent");
    assertThat(result).containsKey("next_session_id");
    assertThat(session.state())
        .containsEntry(MultiAgentStateKeys.HANDOFF_TARGET_AGENT_ID_KEY, "support-agent")
        .containsEntry(MultiAgentStateKeys.HANDOFF_REASON_KEY, "Needs specialist")
        .containsEntry(MultiAgentStateKeys.HANDOFF_MESSAGE_KEY, "Please investigate logs")
        .containsEntry(MultiAgentStateKeys.HANDOFF_SOURCE_AGENT_ID_KEY, "triage-agent")
        .containsEntry(MultiAgentStateKeys.HANDOFF_MAX_HOPS_KEY, 5)
        .containsKey(MultiAgentStateKeys.HANDOFF_NEXT_SESSION_ID_KEY);
    assertThat(telemetry.snapshot()).containsEntry("handoff_requested", 1L);
  }
}
