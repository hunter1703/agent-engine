package com.agentengine.runtime.projection;

import com.agentengine.runtime.actor.SessionFact;
import com.mongodb.client.MongoCollection;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.SingletonActor;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.persistence.jdbc.query.javadsl.JdbcReadJournal;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.jdbc.JdbcSession;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

import static com.agentengine.util.common.beans.AssetClass.AGENT_SESSION;

/**
 * Registers the SessionHistory Pekko Projection on startup, guarded by
 * ClusterSingleton so exactly one node in the cluster runs it at a time. Reads
 * TurnCommitted facts from the JDBC journal and materializes them into the
 * MongoDB {@code session_events} collection via
 * {@link SessionHistoryProjectionHandler}. Offset tracking uses JDBC.
 */
@ApplicationScoped
public class SessionHistoryProjection {

  private static final Logger LOG = LoggerFactory.getLogger(SessionHistoryProjection.class);

  private static final int MIN_SLICE = 0;
  private static final int MAX_SLICE = 1023;

  private final ActorSystem<Void> actorSystem;
  private final MongoCollection<Document> collection;
  private final Supplier<Connection> jdbcConnectionFactory;

  @Inject
  public SessionHistoryProjection(final ActorSystem<Void> actorSystem, final MongoCollection<Document> collection,
      final Supplier<Connection> jdbcConnectionFactory) {
    this.actorSystem = actorSystem;
    this.collection = collection;
    this.jdbcConnectionFactory = jdbcConnectionFactory;
  }

  public void onStart(@Observes final StartupEvent event) {
    LOG.info("Starting SessionHistory projection (slices {}-{})", MIN_SLICE, MAX_SLICE);

    final var sourceProvider = EventSourcedProvider.<SessionFact>eventsBySlices(actorSystem, JdbcReadJournal.Identifier(), AGENT_SESSION,
        MIN_SLICE, MAX_SLICE);

    final var projection = JdbcProjection.atLeastOnceAsync(ProjectionId.of("session-history", "all-slices"), sourceProvider,
        () -> new ConnectionBackedJdbcSession(jdbcConnectionFactory.get()), () -> new SessionHistoryProjectionHandler(collection),
        actorSystem);

    ClusterSingleton.get(actorSystem).init(SingletonActor.of(ProjectionBehavior.create(projection), "session-history-projection"));
  }

  /**
   * Minimal JdbcSession implementation backed by a raw JDBC Connection for offset
   * tracking.
   */
  private static final class ConnectionBackedJdbcSession implements JdbcSession {

    private final Connection connection;

    ConnectionBackedJdbcSession(final Connection connection) {
      this.connection = connection;
    }

    @Override
    public <Result> Result withConnection(final Function<Connection, Result> func) throws Exception {
      return func.apply(connection);
    }

    @Override
    public void commit() {
      try {
        connection.commit();
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void rollback() {
      try {
        connection.rollback();
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() {
      try {
        connection.close();
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
