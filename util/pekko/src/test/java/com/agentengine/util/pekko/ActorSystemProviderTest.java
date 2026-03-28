package com.agentengine.util.pekko;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.mongodb.infra.SQLInfraConfig;
import com.typesafe.config.Config;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ActorSystemProviderTest {

  @AfterEach
  public void cleanupSystemProperties() {
    System.clearProperty("POD_NAME");
    System.clearProperty("POD_NAMESPACE");
  }

  @Test
  public void shouldBuildConfigUsingSqlInfraConfigAndResolvedPodPlaceholders() {
    System.setProperty("POD_NAME", "runtime-0");
    System.setProperty("POD_NAMESPACE", "agent-engine");

    final PekkoConfig pekkoConfig = new PekkoConfig();
    pekkoConfig.setClusterName("test-cluster");
    pekkoConfig.setHostname("${POD_NAME}.agent-engine-runtime-internal.${POD_NAMESPACE}.svc.cluster.local");
    pekkoConfig.setPort(2552);
    pekkoConfig.setSeedNodes(
        List.of("pekko://test-cluster@agent-engine-runtime-0.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552",
            "pekko://test-cluster@agent-engine-runtime-1.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552"));
    pekkoConfig.setSnapshotThreshold(100);

    final SQLInfraConfig sqlConfig = new SQLInfraConfig();
    sqlConfig.setJdbcUrl("jdbc:postgresql://postgres:5432/agent_engine_events");
    sqlConfig.setJdbcUser("agentengine");
    sqlConfig.setJdbcPassword("secret");

    final Config config = ActorSystemProvider.buildConfig(pekkoConfig, sqlConfig);

    assertThat(config.getString("pekko.remote.artery.canonical.hostname"))
        .isEqualTo("runtime-0.agent-engine-runtime-internal.agent-engine.svc.cluster.local");
    assertThat(config.getString("jdbc-journal.slick.db.url")).isEqualTo("jdbc:postgresql://postgres:5432/agent_engine_events");
    assertThat(config.getString("jdbc-journal.slick.db.user")).isEqualTo("agentengine");
    assertThat(config.getString("jdbc-journal.slick.db.password")).isEqualTo("secret");
    assertThat(config.getStringList("pekko.cluster.seed-nodes")).containsExactly(
        "pekko://test-cluster@agent-engine-runtime-0.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552",
        "pekko://test-cluster@agent-engine-runtime-1.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552");
  }
}
