package com.agentengine.util.pekko;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.config.ApplicationConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central access point for the Pekko {@link ActorSystem} and related infrastructure.
 */
@Singleton
public class ActorSystemProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ActorSystemProvider.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final String PEKKO_CLUSTER_ROLES_KEY = "pekko.cluster.roles";
    private static final String PEKKO_JACKSON_MODULES_KEY = "pekko.serialization.jackson.modules";

    private final ApplicationConfig applicationConfig;
    private final InfraMongoRepository repository;
    private final Instance<ShardedEntityDefinition> entityDefinitions;
    private volatile PekkoConfig pekkoConfig;
    private volatile ActorSystem<SpawnProtocol.Command> system;
    private volatile ClusterSharding sharding;

    @Inject
    public ActorSystemProvider(
            final InfraMongoRepository repository,
            final Instance<ShardedEntityDefinition> entityDefinitions,
            final ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.repository = repository;
        this.entityDefinitions = entityDefinitions;
    }

    /**
     * Initializes the Pekko runtime eagerly at application startup so configuration or sharding
     * wiring issues fail fast during deployment instead of on first request.
     * <p>
     * Not initializing in constructor as some ShardedEntityDefinitions require ActorSystemProvider and hence would result into circular dependency
     */
    public void onStart(@Observes final StartupEvent event) {
        this.pekkoConfig = repository.findOneByType(PekkoConfig.TYPE);
        final SQLInfraConfig sqlConfig = repository.findOneByType(SQLInfraConfig.TYPE);
        LOG.info("Creating ActorSystem '{}'", pekkoConfig.getClusterName());
        final Config config = buildConfig(pekkoConfig, sqlConfig);
        final ActorSystem<SpawnProtocol.Command> actorSystem =
                ActorSystem.create(SpawnProtocol.create(), pekkoConfig.getClusterName(), config);
        final ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
        for (final ShardedEntityDefinition definition : entityDefinitions) {
            clusterSharding.init(definition.entity(actorSystem));
            LOG.info("Registered sharded entity: {}", definition.getClass().getSimpleName());
        }
        this.system = actorSystem;
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
        final String hostname = resolve(config.getHostname());
        final List<String> seedNodes = config.getSeedNodes() == null
                ? List.of()
                : config.getSeedNodes().stream().map(this::resolve).toList();
        final String jdbcUrl = resolve(sqlConfig.getJdbcUrl());
        final String jdbcUser = resolve(sqlConfig.getJdbcUser());
        final String jdbcPassword = resolve(sqlConfig.getJdbcPassword());
        final Config baseConfig = ConfigFactory.parseResources("pekko-base.conf");
        final List<String> jacksonModules = Stream.concat(
                        baseConfig.getStringList("pekko.serialization.jackson.jackson-modules").stream(),
                        applicationConfig.getListOfString(PEKKO_JACKSON_MODULES_KEY).stream())
                .toList();
        // Static structure is in pekko-base.conf; dynamic/sensitive values are overlaid via withValue so they are never
        // present in a logged HOCON string.
        return baseConfig
                .withValue("pekko.remote.artery.canonical.hostname", ConfigValueFactory.fromAnyRef(hostname))
                .withValue("pekko.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(config.getPort()))
                .withValue("pekko.remote.artery.bind.port", ConfigValueFactory.fromAnyRef(config.getPort()))
                .withValue("pekko.cluster.seed-nodes", ConfigValueFactory.fromIterable(seedNodes))
                .withValue(
                        "pekko.cluster.roles",
                        ConfigValueFactory.fromIterable(applicationConfig.getListOfString(PEKKO_CLUSTER_ROLES_KEY)))
                .withValue(
                        "pekko.serialization.jackson.jackson-modules", ConfigValueFactory.fromIterable(jacksonModules))
                .withValue("jdbc-journal.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-journal.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-journal.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withValue("jdbc-snapshot-store.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-snapshot-store.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-snapshot-store.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withValue("jdbc-read-journal.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-read-journal.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-read-journal.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withFallback(ConfigFactory.load())
                .resolve();
    }

    private String resolve(final String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return rawValue;
        }
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawValue);
        final StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            final String key = matcher.group(1);
            final String replacement = applicationConfig.getString(key);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
