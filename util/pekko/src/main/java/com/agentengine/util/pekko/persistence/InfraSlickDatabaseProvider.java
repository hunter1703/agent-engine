package com.agentengine.util.pekko.persistence;

import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.sql.SQLClientInfraConfig;
import com.agentengine.util.sql.SQLServerInfraConfig;
import com.typesafe.config.Config;
import io.quarkus.arc.Arc;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.jdbc.db.LazySlickDatabase;
import org.apache.pekko.persistence.jdbc.db.SlickDatabase;
import org.apache.pekko.persistence.jdbc.db.SlickDatabaseProvider;

public class InfraSlickDatabaseProvider
    extends InfraClientFactory<
        SQLClientInfraConfig, SQLServerInfraConfig, InfraSlickDatabaseProvider.Pool>
    implements SlickDatabaseProvider {

  private final ActorSystem system;

  @java.lang.SuppressWarnings("resource")
  public InfraSlickDatabaseProvider(final ActorSystem system) {
    super(
        Arc.container().instance(InfraConfigService.class).get(),
        Arc.container().instance(DistributedCacheManager.class).get(),
        SQLServerInfraConfig.TYPE);
    this.system = system;
  }

  @Override
  public SlickDatabase database(final Config config) {
    return get(infraConfigService.get(config.getString(PekkoUtils.SQL_CLIENT_ID))).slickDatabase();
  }

  @Override
  protected Pool create(final SQLServerInfraConfig serverConfig) {
    final Config baseConfig = system.settings().config().getConfig(PekkoUtils.SLICK);
    return new Pool(new LazySlickDatabase(PekkoUtils.buildSlickConfig(baseConfig, serverConfig), system));
  }

  public record Pool(LazySlickDatabase slickDatabase) implements AutoCloseable {

    @Override
    public void close() {
      slickDatabase.database().close();
    }
  }
}
