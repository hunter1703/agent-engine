package com.agentengine.util.pekko.persistence;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraClientProvisioner;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.sql.SQLClientInfraConfig;
import com.agentengine.util.sql.SQLServerInfraConfig;
import com.agentengine.util.sql.SQLUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.jdbc.testkit.javadsl.SchemaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PekkoEventStoreProvisioner extends InfraClientProvisioner {

  private static final Logger LOG = LoggerFactory.getLogger(PekkoEventStoreProvisioner.class);

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final Config SMALL_POOL =
      ConfigFactory.parseString("db { numThreads = 1, maxConnections = 1, minConnections = 1 }");

  private final InfraConfigService infraConfigService;

  @Inject
  public PekkoEventStoreProvisioner(
      final InfraConfigService infraConfigService, final ApplicationConfig applicationConfig) {
    super(applicationConfig);
    this.infraConfigService = infraConfigService;
  }

  public void provision(final int customerId, final String serverId) {
    final SQLClientInfraConfig clientConfig =
        SQLUtils.clientConfig(
            PekkoUtils.PEKKO_STORE, customerId, resolvedServerId(ServerType.SQL_SERVER, serverId));
    infraConfigService.save(clientConfig);
    setup(clientConfig);
  }

  private void setup(final SQLClientInfraConfig clientConfig) {
    final SQLServerInfraConfig server =
        infraConfigService.getServer(ServerType.SQL_SERVER, clientConfig);
    createSchema(server, clientConfig.schema());
    createTables(server, clientConfig);
  }

  private static void createSchema(final SQLServerInfraConfig server, final String schema) {
    final String quotedSchema = SQLUtils.quoteIdentifier(schema);
    try (Connection connection =
            DriverManager.getConnection(
                server.jdbcUrl(server.getDatabase()), server.getUsername(), server.getPassword());
        Statement create = connection.createStatement()) {
      create.execute("CREATE SCHEMA IF NOT EXISTS " + quotedSchema);
    } catch (final SQLException ex) {
      throw new IllegalStateException("Failed to create schema '" + schema + "'", ex);
    }
  }

  private static void createTables(
      final SQLServerInfraConfig serverConfig, final SQLClientInfraConfig clientConfig) {
    final ActorSystem system =
        ActorSystem.create("event-store-setup", config(serverConfig, clientConfig));
    try {
      SchemaUtils.createIfNotExists(system)
          .toCompletableFuture()
          .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      LOG.info(
          "Event store tables ensured in schema '{}' of database '{}'",
          clientConfig.schema(),
          serverConfig.getDatabase());
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while setting up the event store", ex);
    } catch (final ExecutionException | TimeoutException ex) {
      throw new IllegalStateException("Failed to set up the event store", ex);
    } finally {
      system.terminate();
      system.getWhenTerminated().toCompletableFuture().join();
    }
  }

  private static Config config(
      final SQLServerInfraConfig serverConfig, final SQLClientInfraConfig clientConfig) {
    final Config base = ConfigFactory.defaultReference();
    return base.withValue(
            PekkoUtils.JOURNAL,
            plugin(base, PekkoUtils.JOURNAL, PekkoUtils.JOURNAL_TABLES, serverConfig, clientConfig))
        .withValue(
            PekkoUtils.SNAPSHOT_STORE,
            plugin(
                base,
                PekkoUtils.SNAPSHOT_STORE,
                PekkoUtils.SNAPSHOT_TABLES,
                serverConfig,
                clientConfig));
  }

  private static ConfigValue plugin(
      final Config reference,
      final String plugin,
      final List<String> tables,
      final SQLServerInfraConfig serverConfig,
      final SQLClientInfraConfig clientConfig) {
    final Config slick =
        PekkoUtils.buildSlickConfig(
            SMALL_POOL.withFallback(reference.getConfig(plugin + ".slick")), serverConfig);
    return PekkoUtils.withSchema(reference.getConfig(plugin), clientConfig.schema(), tables)
        .withValue("slick", slick.root())
        .root();
  }
}
