package com.agentengine.util.pekko;

import com.agentengine.util.common.EnvUtils;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import com.agentengine.util.mongodb.infra.SQLInfraConfig;
import com.agentengine.util.pekko.actor.ShardedEntityDefinition;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Central access point for the Pekko {@link ActorSystem} and related infrastructure. */
@Singleton
public class ActorSystemProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ActorSystemProvider.class);
    private static final int PEKKO_PORT = 2552;
    private static final String PEKKO_CLUSTER_ROLES_KEY = "pekko.cluster.roles";
    private static final String JACKSON_MODULES_KEY = "pekko.serialization.jackson.jackson-modules";

    private final ApplicationConfig applicationConfig;
    private final InfraConfigService infraConfigService;
    private final Instance<ShardedEntityDefinition> entityDefinitions;
    private volatile PekkoConfig pekkoConfig;
    private volatile ActorSystem<SpawnProtocol.Command> system;
    private volatile ClusterSharding sharding;

    @Inject
    public ActorSystemProvider(
            final InfraConfigService infraConfigService,
            final Instance<ShardedEntityDefinition> entityDefinitions,
            final ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.infraConfigService = infraConfigService;
        this.entityDefinitions = entityDefinitions;
    }

    /**
     * Initializes the Pekko runtime eagerly at application startup so configuration or sharding
     * wiring issues fail fast during deployment instead of on first request.
     *
     * <p>Not initializing in constructor as some ShardedEntityDefinitions require ActorSystemProvider
     * and hence would result into circular dependency.
     */
    public void onStart(@Observes final StartupEvent event) {
        this.pekkoConfig = infraConfigService.findById(PekkoConfig.CATEGORY, PekkoConfig.TYPE, PekkoConfig.CONFIG_ID);
        final SQLInfraConfig sqlConfig = infraConfigService.findById(
                SQLInfraConfig.CATEGORY, SQLInfraConfig.TYPE, SQLInfraConfig.DEFAULT_CONFIG_ID);
        LOG.info("Creating ActorSystem '{}'", pekkoConfig.getClusterName());
        final Config config = buildConfig(pekkoConfig, sqlConfig);
        this.system = ActorSystem.create(SpawnProtocol.create(), pekkoConfig.getClusterName(), config);
        // Publish system before initialising sharding: remember-entities triggers entity recovery
        // on actor dispatcher threads immediately upon init(), and those actors call back into
        // actorSystemProvider.system(). If system is still null at that point we get a NPE.
        final ClusterSharding clusterSharding = ClusterSharding.get(this.system);
        for (final ShardedEntityDefinition definition : entityDefinitions) {
            clusterSharding.init(definition.entity(this.system));
            LOG.info("Registered sharded entity: {}", definition.getClass().getSimpleName());
        }
        this.sharding = clusterSharding;
    }

    public PekkoConfig pekkoConfig() {
        return pekkoConfig;
    }

    public ActorSystem<SpawnProtocol.Command> system() {
        return system;
    }

    public ClusterSharding sharding() {
        return sharding;
    }

    public <Command> EntityRef<Command> entityRefFor(final EntityTypeKey<Command> key, final String id) {
        return sharding.entityRefFor(key, id);
    }

    private Config buildConfig(final PekkoConfig config, final SQLInfraConfig sqlConfig) {
        final String hostname = EnvUtils.getHostname();
        final List<String> seedNodes = config.getSeedNodes() == null ? List.of() : config.getSeedNodes();
        final String jdbcUrl = sqlConfig.getJdbcUrl();
        final String jdbcUser = sqlConfig.getJdbcUser();
        final String jdbcPassword = sqlConfig.getJdbcPassword();
        // Parse pekko-base.conf with reference.conf as fallback so that its += operators (e.g. for
        // jackson-modules) resolve against Pekko's defaults rather than starting from an empty list.
        // This preserves PekkoJacksonModule (the ActorRef serializer) from Pekko's reference.conf.
        final Config baseConfig = ConfigFactory.parseResources("pekko-base.conf")
                .withFallback(ConfigFactory.defaultReference())
                .resolve();
        final List<String> extraModules = applicationConfig.getListOfString("pekko.serialization.modules");
        final List<String> baseModules = baseConfig.getStringList(JACKSON_MODULES_KEY);
        final List<String> allJacksonModules =
                Stream.concat(baseModules.stream(), extraModules.stream()).toList();
        // Static structure is in pekko-base.conf; dynamic/sensitive values are overlaid via withValue
        // so they are never present in a logged HOCON string.
        return baseConfig
                .withValue("pekko.remote.artery.canonical.hostname", ConfigValueFactory.fromAnyRef(hostname))
                .withValue("pekko.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(PEKKO_PORT))
                .withValue("pekko.remote.artery.bind.port", ConfigValueFactory.fromAnyRef(PEKKO_PORT))
                .withValue("pekko.cluster.seed-nodes", ConfigValueFactory.fromIterable(seedNodes))
                .withValue(
                        "pekko.cluster.roles",
                        ConfigValueFactory.fromIterable(applicationConfig.getListOfString(PEKKO_CLUSTER_ROLES_KEY)))
                .withValue(JACKSON_MODULES_KEY, ConfigValueFactory.fromIterable(allJacksonModules))
                .withValue("jdbc-journal.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-journal.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-journal.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withValue("jdbc-snapshot-store.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-snapshot-store.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-snapshot-store.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withValue("jdbc-read-journal.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-read-journal.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-read-journal.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withValue(
                        "pekko-persistence-jdbc.shared-databases.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue(
                        "pekko-persistence-jdbc.shared-databases.slick.db.user",
                        ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue(
                        "pekko-persistence-jdbc.shared-databases.slick.db.password",
                        ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withFallback(ConfigFactory.load())
                .resolve();
    }
}
