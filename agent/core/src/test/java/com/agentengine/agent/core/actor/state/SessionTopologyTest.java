package com.agentengine.agent.core.actor.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionTopologyTest {

  @Test
  void shouldExposeNullParentIdsForRootTopology() {
    final SessionTopology topology = SessionTopology.root("root-agent", "root-session");

    assertThat(topology.isRoot()).isTrue();
    assertThat(topology.rootSessionId()).isEqualTo("root-session");
    assertThat(topology.parentSessionId()).isNull();
    assertThat(topology.parentAgentId()).isNull();
  }

  @Test
  void shouldExposeParentIdsForChildTopology() {
    final SessionTopology topology =
        SessionTopology.child(
            "child-agent", "child-session", "root-session", "parent-session", "parent-agent");

    assertThat(topology.isRoot()).isFalse();
    assertThat(topology.rootSessionId()).isEqualTo("root-session");
    assertThat(topology.parentSessionId()).isEqualTo("parent-session");
    assertThat(topology.parentAgentId()).isEqualTo("parent-agent");
  }
}
