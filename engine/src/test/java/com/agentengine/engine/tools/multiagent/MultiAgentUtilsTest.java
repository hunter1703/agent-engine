package com.agentengine.engine.tools.multiagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class MultiAgentUtilsTest {

  @Test
  void consumeHandoffReturnsRequestAndClearsStateKeys() {
    final ConcurrentHashMap<String, Object> state =
        new ConcurrentHashMap<>(
            Map.of(
                MultiAgentStateKeys.HANDOFF_TARGET_AGENT_ID_KEY,
                "support-agent",
                MultiAgentStateKeys.HANDOFF_MESSAGE_KEY,
                "resume from diagnostics",
                MultiAgentStateKeys.HANDOFF_REASON_KEY,
                "specialist",
                MultiAgentStateKeys.HANDOFF_NEXT_SESSION_ID_KEY,
                "session-2",
                MultiAgentStateKeys.HANDOFF_MAX_HOPS_KEY,
                3,
                MultiAgentStateKeys.HANDOFF_SOURCE_AGENT_ID_KEY,
                "triage-agent",
                MultiAgentStateKeys.HANDOFF_REQUESTED_AT_KEY,
                123L));

    final MultiAgentUtils.HandoffRequest request =
        MultiAgentUtils.consumeHandoff(state, "fallback message");

    assertThat(request).isNotNull();
    assertThat(request.targetAgentId()).isEqualTo("support-agent");
    assertThat(request.nextSessionId()).isEqualTo("session-2");
    assertThat(request.message()).isEqualTo("resume from diagnostics");
    assertThat(request.maxHops()).isEqualTo(3);
    assertThat(state)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_TARGET_AGENT_ID_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_MESSAGE_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_REASON_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_NEXT_SESSION_ID_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_MAX_HOPS_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_SOURCE_AGENT_ID_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_REQUESTED_AT_KEY);
  }

  @Test
  void consumeHandoffUsesFallbackMessageWhenStateMessageMissing() {
    final ConcurrentHashMap<String, Object> state =
        new ConcurrentHashMap<>(
            Map.of(MultiAgentStateKeys.HANDOFF_TARGET_AGENT_ID_KEY, "support-agent"));

    final MultiAgentUtils.HandoffRequest request =
        MultiAgentUtils.consumeHandoff(state, "fallback message");

    assertThat(request).isNotNull();
    assertThat(request.message()).isEqualTo("fallback message");
    assertThat(request.maxHops()).isEqualTo(0);
  }
}
