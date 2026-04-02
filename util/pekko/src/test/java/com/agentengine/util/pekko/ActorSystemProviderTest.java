package com.agentengine.util.pekko;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.mongodb.infra.SQLInfraConfig;
import com.typesafe.config.Config;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ActorSystemProviderTest {

    @Test
    public void shouldBuildConfigUsingSqlInfraConfigAndResolvedPodPlaceholders() {
        final PekkoConfig pekkoConfig = new PekkoConfig();
        pekkoConfig.setClusterName("test-cluster");
        pekkoConfig.setHostname("${POD_NAME}.agent-engine-runtime-internal.${POD_NAMESPACE}.svc.cluster.local");
        pekkoConfig.setPort(2552);
        pekkoConfig.setSeedNodes(
                List.of(
                        "pekko://test-cluster@agent-engine-runtime-0.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552",
                        "pekko://test-cluster@agent-engine-runtime-1.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552"));
        pekkoConfig.setSnapshotThreshold(100);

        final SQLInfraConfig sqlConfig = new SQLInfraConfig();
        sqlConfig.setJdbcUrl("jdbc:postgresql://postgres:5432/agent_engine_events");
        sqlConfig.setJdbcUser("agentengine");
        sqlConfig.setJdbcPassword("secret");

        final Map<String, String> values = Map.of(
                "POD_NAME", "runtime-0",
                "POD_NAMESPACE", "agent-engine");

        final Config config = ActorSystemProvider.buildConfig(pekkoConfig, sqlConfig, List.of("runtime"), key -> {
            final String value = values.get(key);
            if (value != null) {
                return value;
            }
            throw new IllegalStateException("Missing value for runtime config placeholder: " + key);
        });

        assertThat(config.getString("pekko.remote.artery.canonical.hostname"))
                .isEqualTo("runtime-0.agent-engine-runtime-internal.agent-engine.svc.cluster.local");
        assertThat(config.getString("jdbc-journal.slick.db.url"))
                .isEqualTo("jdbc:postgresql://postgres:5432/agent_engine_events");
        assertThat(config.getString("jdbc-journal.slick.db.user")).isEqualTo("agentengine");
        assertThat(config.getString("jdbc-journal.slick.db.password")).isEqualTo("secret");
        assertThat(config.getStringList("pekko.cluster.seed-nodes"))
                .containsExactly(
                        "pekko://test-cluster@agent-engine-runtime-0.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552",
                        "pekko://test-cluster@agent-engine-runtime-1.agent-engine-runtime-internal.agent-engine.svc.cluster.local:2552");
        assertThat(config.getStringList("pekko.cluster.roles")).containsExactly("runtime");
    }

    @Test
    public void shouldResolvePlaceholdersFromProvidedResolver() {
        final PekkoConfig pekkoConfig = new PekkoConfig();
        pekkoConfig.setClusterName("test-cluster");
        pekkoConfig.setHostname("${HOSTNAME_VALUE}");
        pekkoConfig.setPort(2552);
        pekkoConfig.setSeedNodes(List.of("pekko://test-cluster@${SEED_HOST}:2552"));
        pekkoConfig.setSnapshotThreshold(100);

        final SQLInfraConfig sqlConfig = new SQLInfraConfig();
        sqlConfig.setJdbcUrl("${SQL_URL}");
        sqlConfig.setJdbcUser("${SQL_USER}");
        sqlConfig.setJdbcPassword("${SQL_PASSWORD}");

        final Map<String, String> values = Map.of(
                "HOSTNAME_VALUE", "runtime-0.agent-engine.svc.cluster.local",
                "SEED_HOST", "runtime-0.agent-engine.svc.cluster.local",
                "SQL_URL", "jdbc:postgresql://postgres:5432/agent_engine_events",
                "SQL_USER", "agentengine",
                "SQL_PASSWORD", "secret");

        final Config config = ActorSystemProvider.buildConfig(pekkoConfig, sqlConfig, List.of("runtime"), key -> {
            final String value = values.get(key);
            if (value != null) {
                return value;
            }
            throw new IllegalStateException("Missing value for runtime config placeholder: " + key);
        });

        assertThat(config.getString("pekko.remote.artery.canonical.hostname"))
                .isEqualTo("runtime-0.agent-engine.svc.cluster.local");
        assertThat(config.getString("jdbc-journal.slick.db.url"))
                .isEqualTo("jdbc:postgresql://postgres:5432/agent_engine_events");
        assertThat(config.getString("jdbc-journal.slick.db.user")).isEqualTo("agentengine");
        assertThat(config.getString("jdbc-journal.slick.db.password")).isEqualTo("secret");
        assertThat(config.getStringList("pekko.cluster.seed-nodes"))
                .containsExactly("pekko://test-cluster@runtime-0.agent-engine.svc.cluster.local:2552");
    }
}
