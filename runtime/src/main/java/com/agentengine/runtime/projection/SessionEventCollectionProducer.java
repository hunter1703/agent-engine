package com.agentengine.runtime.projection;

import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

import static com.mongodb.client.model.Indexes.ascending;

/**
 * Produces CDI beans needed by the SessionHistory projection: the
 * {@code session_events} MongoDB collection and a JDBC connection factory for
 * projection offset tracking. Also applies the offset store DDL and creates the
 * MongoDB unique index on startup.
 */
@ApplicationScoped
public class SessionEventCollectionProducer {

  private static final Logger LOG = LoggerFactory.getLogger(SessionEventCollectionProducer.class);
  private static final String DATABASE_NAME = "AGENT_ENGINE";
  private static final String COLLECTION_NAME = "session_events";

  private final MongoCollection<Document> collection;
  private final DataSource dataSource;

  @Inject
  public SessionEventCollectionProducer(final MongoClientFactory mongoClientFactory, final DataSource dataSource) {
    this.collection = mongoClientFactory.getClient().getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME);
    this.dataSource = dataSource;
  }

  @Produces
  @Singleton
  public MongoCollection<Document> sessionEventsCollection() {
    return collection;
  }

  @Produces
  @Singleton
  public Supplier<Connection> jdbcConnectionFactory() {
    return () -> {
      try {
        return dataSource.getConnection();
      } catch (final SQLException e) {
        throw new RuntimeException("Failed to acquire JDBC connection for projection", e);
      }
    };
  }

  public void onStart(@Observes final StartupEvent event) {
    collection.createIndex(ascending("sessionId", "sequence"), new IndexOptions().unique(true).background(true));
    applyProjectionOffsetStoreDdl();
  }

  private void applyProjectionOffsetStoreDdl() {
    try (final Connection conn = dataSource.getConnection(); final var stmt = conn.createStatement()) {
      stmt.execute("""
          CREATE TABLE IF NOT EXISTS pekko_projection_offset_store (
            projection_name VARCHAR(255) NOT NULL,
            projection_key  VARCHAR(255) NOT NULL,
            current_offset  VARCHAR(255) NOT NULL,
            manifest        VARCHAR(4)   NOT NULL,
            mergeable       BOOLEAN      NOT NULL,
            last_updated    BIGINT        NOT NULL,
            PRIMARY KEY (projection_name, projection_key)
          )
          """);
    } catch (final SQLException e) {
      throw new RuntimeException("Failed to initialise projection offset store", e);
    }
  }
}
