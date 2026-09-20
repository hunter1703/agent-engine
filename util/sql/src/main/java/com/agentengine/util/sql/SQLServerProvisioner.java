package com.agentengine.util.sql;

import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraServerProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Singleton
public class SQLServerProvisioner extends InfraServerProvisioner<SQLServerInfraConfig> {

  @Inject
  public SQLServerProvisioner(final InfraConfigService infraConfigService) {
    super(infraConfigService);
  }

  @Override
  protected void setup(final SQLServerInfraConfig server) {
    if (server.engineType() != SQLServerInfraConfig.Engine.POSTGRES) {
      throw new IllegalStateException("Unsupported SQL engine");
    }
    final String database = server.getDatabase();
    if (!database.matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("Invalid database name '" + database + "'");
    }
    try (final Connection connection =
            DriverManager.getConnection(
                server.jdbcUrl("postgres"), server.getUsername(), server.getPassword());
        final PreparedStatement exists =
            connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
      exists.setString(1, database);
      try (final ResultSet result = exists.executeQuery()) {
        if (result.next()) {
          return;
        }
      }
      try (Statement create = connection.createStatement()) {
        create.execute("CREATE DATABASE \"%s\"".formatted(database));
      }
    } catch (final SQLException ex) {
      throw new IllegalStateException("Failed to create database '" + database + "'", ex);
    }
  }
}
