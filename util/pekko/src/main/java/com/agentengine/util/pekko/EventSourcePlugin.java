package com.agentengine.util.pekko;

import com.typesafe.config.Config;
import java.util.Optional;

public interface EventSourcePlugin {

  String journalPluginId();

  String snapshotPluginId();

  Optional<Config> journalPluginConfig();

  Optional<Config> snapshotPluginConfig();

  class NoPlugin implements EventSourcePlugin {
    public static final NoPlugin INSTANCE = new NoPlugin();

    @Override
    public String journalPluginId() {
      return "";
    }

    @Override
    public String snapshotPluginId() {
      return "";
    }

    @Override
    public Optional<Config> journalPluginConfig() {
      return Optional.empty();
    }

    @Override
    public Optional<Config> snapshotPluginConfig() {
      return Optional.empty();
    }
  }
}
