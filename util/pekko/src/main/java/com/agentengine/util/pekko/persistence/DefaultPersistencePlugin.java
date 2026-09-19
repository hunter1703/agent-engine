package com.agentengine.util.pekko.persistence;

import com.agentengine.util.context.UserContext;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.pekko.EventSourcePlugin;
import com.typesafe.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
public class DefaultPersistencePlugin implements EventSourcePlugin {

  private final PersistencePlugin delegate;

  @Inject
  public DefaultPersistencePlugin(final InfraConfigService infraConfigService) {
    this.delegate = new PersistencePlugin(UserContext.SYSTEM.customerId(), infraConfigService);
  }

  @Override
  public String journalPluginId() {
    return delegate.journalPluginId();
  }

  @Override
  public String snapshotPluginId() {
    return delegate.snapshotPluginId();
  }

  @Override
  public Optional<Config> journalPluginConfig() {
    return delegate.journalPluginConfig();
  }

  @Override
  public Optional<Config> snapshotPluginConfig() {
    return delegate.snapshotPluginConfig();
  }
}
