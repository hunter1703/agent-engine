package com.agentengine.util.pekko.persistence;

import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.pekko.EventSourcePlugin;
import com.agentengine.util.sql.SQLClientInfraConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.List;
import java.util.Optional;

public class PersistencePlugin implements EventSourcePlugin {

  private final int customerId;
  private final InfraConfigService infraConfigService;

  public PersistencePlugin(final int customerId, final InfraConfigService infraConfigService) {
    this.customerId = customerId;
    this.infraConfigService = infraConfigService;
  }

  @Override
  public String journalPluginId() {
    return "customer-" + customerId + "-journal";
  }

  @Override
  public Optional<Config> journalPluginConfig() {
    return Optional.of(pluginConfig(journalPluginId(), PekkoUtils.JOURNAL, PekkoUtils.JOURNAL_TABLES));
  }

  @Override
  public String snapshotPluginId() {
    return "customer-" + customerId + "-snapshot-store";
  }

  @Override
  public Optional<Config> snapshotPluginConfig() {
    return Optional.of(pluginConfig(snapshotPluginId(), PekkoUtils.SNAPSHOT_STORE, PekkoUtils.SNAPSHOT_TABLES));
  }

  // only contains jdbc config needed per actor
  private Config pluginConfig(
      final String pluginId, final String pluginType, final List<String> tables) {
    final SQLClientInfraConfig client = PekkoUtils.sqlClient(infraConfigService, customerId);
    final Config base = ConfigFactory.defaultReference().getConfig(pluginType);
    return ConfigFactory.empty()
        .withValue(pluginId, PekkoUtils.buildConfigForClient(base, client, tables).root());
  }
}
