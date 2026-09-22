package com.agentengine.util.pekko.persistence;

import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraClientProvisioner;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.sql.SQLClientInfraConfig;
import com.agentengine.util.sql.SQLServerInfraConfig;
import com.agentengine.util.sql.SQLUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PekkoEventStoreProvisioner extends InfraClientProvisioner {

  private static final Logger LOG = LoggerFactory.getLogger(PekkoEventStoreProvisioner.class);
  private static final String EVENT_STORE_SETUP_SQL_PATH = "schema/postgres/event-store.sql";

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
    final String schema = clientConfig.schema();
    final String quotedSchema = SQLUtils.quoteIdentifier(schema);
    try (final Connection connection =
            DriverManager.getConnection(
                server.jdbcUrl(server.getDatabase()), server.getUsername(), server.getPassword());
        final Statement statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA IF NOT EXISTS " + quotedSchema);
      // this sets the schema for the commands to follow so we dont need to use fully qualified
      // table names
      statement.execute("SET search_path TO " + quotedSchema);
      for (final String ddl : ddlStatements()) {
        statement.execute(ddl);
      }
      LOG.info(
          "Event store tables ensured in schema '{}' of database '{}'",
          schema,
          server.getDatabase());
    } catch (final SQLException ex) {
      throw new IllegalStateException(
          "Failed to set up event store in schema '" + schema + "'", ex);
    }
  }

  private static List<String> ddlStatements() {
    final String script = ResourceUtils.loadResourceAsString(EVENT_STORE_SETUP_SQL_PATH);
    if (StringUtils.isBlank(script)) {
      throw new IllegalStateException(
          "Missing DDL script on classpath: " + EVENT_STORE_SETUP_SQL_PATH);
    }
    return Arrays.stream(script.split(";"))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .toList();
  }
}
