package com.agentengine.util.pekko.persistence;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.sql.SQLClientInfraConfig;
import com.agentengine.util.sql.SQLServerInfraConfig;
import com.agentengine.util.sql.SQLUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import java.util.List;

public final class PekkoUtils {

  public static final String SLICK = "pekko-persistence-jdbc.shared-databases.slick";
  public static final String JOURNAL = "jdbc-journal";
  public static final String SNAPSHOT_STORE = "jdbc-snapshot-store";
  public static final List<String> JOURNAL_TABLES = List.of("event_journal", "event_tag");
  public static final List<String> SNAPSHOT_TABLES = List.of("snapshot");
  public static final String SQL_CLIENT_ID = "sql-client-id";
  public static final String PEKKO_STORE = "PEKKO";
  public static final String PERSISTENCE_DISPATCHER = "pekko.actor.persistence-dispatcher";

  private PekkoUtils() {}

  public static SQLClientInfraConfig sqlClient(
      final InfraConfigService infraConfigService, final int customerId) {
    return infraConfigService.get(SQLUtils.clientId(PEKKO_STORE, customerId));
  }

  public static Config buildConfigForClient(
      final Config plugin, final SQLClientInfraConfig clientConfig, final List<String> tables) {
    return withSchema(plugin, clientConfig.schema(), tables)
        .withValue(SQL_CLIENT_ID, ConfigValueFactory.fromAnyRef(clientConfig.getId()))
        .withValue("plugin-dispatcher", ConfigValueFactory.fromAnyRef(PERSISTENCE_DISPATCHER));
  }

  public static Config withSchema(
      final Config baseConfig, final String schema, final List<String> tables) {
    Config result = baseConfig;
    for (final String table : tables) {
      result =
          result.withValue(
              "tables." + table + ".schemaName", ConfigValueFactory.fromAnyRef(schema));
    }
    return result;
  }

  public static Config buildSlickConfig(
      final Config baseConfig, final SQLServerInfraConfig serverConfig) {
    if (StringUtils.isBlank(serverConfig.getDatabase())) {
      throw new IllegalStateException(
          "SQL server '%s' has no database".formatted(serverConfig.getId()));
    }
    final SQLServerInfraConfig.Engine engine = serverConfig.engineType();
    return baseConfig
        .withValue("profile", ConfigValueFactory.fromAnyRef(slickProfile(engine)))
        .withValue("db.driver", ConfigValueFactory.fromAnyRef(engine.driverClass()))
        .withValue(
            "db.url",
            ConfigValueFactory.fromAnyRef(serverConfig.jdbcUrl(serverConfig.getDatabase())))
        .withValue("db.user", ConfigValueFactory.fromAnyRef(serverConfig.getUsername()))
        .withValue("db.password", ConfigValueFactory.fromAnyRef(serverConfig.getPassword()));
  }

  private static String slickProfile(final SQLServerInfraConfig.Engine engine) {
    return switch (engine) {
      case POSTGRES -> "slick.jdbc.PostgresProfile$";
      case UNKNOWN -> throw new IllegalStateException("Unsupported SQL engine");
    };
  }
}
