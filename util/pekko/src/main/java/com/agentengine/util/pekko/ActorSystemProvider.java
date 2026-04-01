package com.agentengine.util.pekko;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import com.agentengine.util.mongodb.infra.SQLInfraConfig;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.stream.Materializer;

/**
 * CDI producer for the Pekko {@link ActorSystem}. One system per application; consumers inject
 * {@code ActorSystem<SpawnProtocol.Command>} directly.
 */
@Singleton
public class ActorSystemProvider {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final String DEFAULT_JDBC_DRIVER = "org.postgresql.Driver";
    private final PekkoConfig pekkoConfig;
    private final LazyLoader<ActorSystem<SpawnProtocol.Command>> system;
    private final LazyLoader<Materializer> materializer;

    public ActorSystemProvider(
            final InfraMongoRepository repository,
            final Instance<ShardedEntity.ShardedEntityDefinition<?, ?>> definitions) {
        this.pekkoConfig = repository.findOneByType(PekkoConfig.TYPE);
        this.system = new LazyLoader<>(() -> {
            final SQLInfraConfig sqlConfig = repository.findOneByType(SQLInfraConfig.TYPE);
            final ActorSystem<SpawnProtocol.Command> actorSystem = ActorSystem.create(
                    SpawnProtocol.create(), this.pekkoConfig.getClusterName(), buildConfig(this.pekkoConfig, sqlConfig));
            final ClusterSharding sharding = ClusterSharding.get(actorSystem);
            for (final ShardedEntity.ShardedEntityDefinition<?, ?> definition : definitions) {
                sharding.init(definition.entity());
            }
            return actorSystem;
        });
        this.materializer = new LazyLoader<>(() -> Materializer.createMaterializer(system.get()));
    }

    @Produces
    @Singleton
    public PekkoConfig pekkoConfig() {
        return pekkoConfig;
    }

    @Produces
    @Singleton
    public ActorSystem<SpawnProtocol.Command> system() {
        return system.get();
    }

    @Produces
    @Singleton
    public Materializer materializer() {
        return materializer.get();
    }

    public static Config buildConfig(final PekkoConfig config, final SQLInfraConfig sqlConfig) {
        final String hostname = resolve(config.getHostname());
        final List<String> seedNodes = config.getSeedNodes() == null
                ? List.of()
                : config.getSeedNodes().stream()
                        .map(ActorSystemProvider::resolve)
                        .toList();
        final String jdbcUrl = resolve(sqlConfig.getJdbcUrl());
        final String jdbcUser = resolve(sqlConfig.getJdbcUser());
        final String jdbcPassword = resolve(sqlConfig.getJdbcPassword());
        // Credentials are injected via withValue so they never appear in a logged HOCON string
        final String hocon = """
        pekko {
          actor {
            provider = cluster
            default-dispatcher.executor = virtual-thread-executor
          }
          remote.artery.canonical {
            hostname = "%s"
            port = %d
          }
          cluster.seed-nodes = [%s]
          serialization.bindings {
            "com.agentengine.util.pekko.PekkoSerializable" = jackson-cbor
          }
          persistence {
            journal.plugin  = "jdbc-journal"
            snapshot-store.plugin = "jdbc-snapshot-store"
          }
        }
        jdbc-journal.slick {
          profile = "slick.jdbc.PostgresProfile$"
          db.driver = "%s"
        }
        jdbc-snapshot-store.slick {
          profile = "slick.jdbc.PostgresProfile$"
          db.driver = "%s"
        }
        """.formatted(
                        hostname,
                        config.getPort(),
                        seedNodes.stream().map(StringUtils::wrapInQuotes).collect(Collectors.joining(", ")),
                        DEFAULT_JDBC_DRIVER,
                        DEFAULT_JDBC_DRIVER);
        return ConfigFactory.parseString(hocon)
                .withValue("jdbc-journal.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-journal.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-journal.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withValue("jdbc-snapshot-store.slick.db.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue("jdbc-snapshot-store.slick.db.user", ConfigValueFactory.fromAnyRef(jdbcUser))
                .withValue("jdbc-snapshot-store.slick.db.password", ConfigValueFactory.fromAnyRef(jdbcPassword))
                .withFallback(ConfigFactory.load());
    }

    private static String resolve(final String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return rawValue;
        }
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawValue);
        final StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            final String key = matcher.group(1);
            final String replacement = resolveValue(key);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String resolveValue(final String key) {
        final String fromEnv = System.getenv(key);
        if (StringUtils.isNotBlank(fromEnv)) {
            return fromEnv;
        }
        final String fromProperty = System.getProperty(key);
        if (StringUtils.isNotBlank(fromProperty)) {
            return fromProperty;
        }
        throw new IllegalStateException("Missing value for runtime config placeholder: " + key);
    }
}
