package com.agentengine.util.sql;

import com.agentengine.util.common.Secure;
import com.agentengine.util.infra.InfraConfig;
import com.agentengine.util.infra.ServerType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.sql.SQLServerInfraConfig")
public class SQLServerInfraConfig extends InfraConfig {
  private String engine = Engine.UNKNOWN.name();
  private String host;
  private Integer port;
  private String database;
  private String username;
  @Secure private String password;

  private Map<String, String> properties = new HashMap<>();

  public SQLServerInfraConfig() {
    setType(ServerType.SQL_SERVER.name());
  }

  @Override
  public String getId() {
    return ServerType.SQL_SERVER + ":" + getServerId();
  }

  public String jdbcUrl(final String databaseName) {
    final Engine engineType = engineType();
    return engineType.jdbcUrl(
        host, port == null ? engineType.defaultPort() : port, databaseName, properties);
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(final String engine) {
    this.engine = engine;
  }

  public Engine engineType() {
    return Engine.valueOfOrUnknown(engine);
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(final Integer port) {
    this.port = port;
  }

  /** The database all the server's stores are kept in, one schema per customer. */
  public String getDatabase() {
    return database;
  }

  public void setDatabase(final String database) {
    this.database = database;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public void setProperties(final Map<String, String> properties) {
    this.properties = properties == null ? new HashMap<>() : new HashMap<>(properties);
  }

  public enum Engine {
    POSTGRES(5432, "org.postgresql.Driver") {
      @Override
      String jdbcUrl(
          final String host,
          final int port,
          final String database,
          final Map<String, String> properties) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database + queryString(properties);
      }
    },
    UNKNOWN(-1, null) {
      @Override
      String jdbcUrl(
          final String host,
          final int port,
          final String database,
          final Map<String, String> properties) {
        throw new IllegalStateException("Unsupported SQL engine");
      }
    };

    private final int defaultPort;
    private final String driverClass;

    Engine(final int defaultPort, final String driverClass) {
      this.defaultPort = defaultPort;
      this.driverClass = driverClass;
    }

    public int defaultPort() {
      return defaultPort;
    }

    public String driverClass() {
      return driverClass;
    }

    abstract String jdbcUrl(String host, int port, String database, Map<String, String> properties);

    public static Engine valueOfOrUnknown(final String value) {
      if (value == null) {
        return UNKNOWN;
      }
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }

    private static String queryString(final Map<String, String> properties) {
      if (properties == null || properties.isEmpty()) {
        return "";
      }
      return new TreeMap<>(properties)
          .entrySet().stream()
              .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
              .collect(Collectors.joining("&", "?", ""));
    }

    private static String encode(final String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
  }
}
