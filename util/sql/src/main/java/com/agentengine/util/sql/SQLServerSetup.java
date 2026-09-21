package com.agentengine.util.sql;

import com.agentengine.util.infra.InfraSetup;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Singleton
public class SQLServerSetup implements InfraSetup<SQLServerInfraConfig> {

  @Override
  public Class<SQLServerInfraConfig> configType() {
    return SQLServerInfraConfig.class;
  }

  @Override
  public void setup(final SQLServerInfraConfig server) {
    if (server.engineType() != SQLServerInfraConfig.Engine.POSTGRES) {
      throw new IllegalStateException("Unsupported SQL engine");
    }
    final String database = server.getDatabase();
    final String quotedDatabase = SQLUtils.quoteIdentifier(database);
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
      try (final Statement create = connection.createStatement()) {
        create.execute("CREATE DATABASE " + quotedDatabase);
      }
    } catch (final SQLException ex) {
      throw new IllegalStateException("Failed to create database '" + database + "'", ex);
    }
  }
}
