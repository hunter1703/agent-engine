package com.agentengine.util.pekko;

import com.agentengine.util.pekko.actor.ShardedEntity;
import com.typesafe.config.Config;

import java.util.Optional;

public abstract class AbstractShardedEntity<Command, Event, State> extends ShardedEntity<Command, Event, State> {
    private final EventSourcePlugin plugin;

    public AbstractShardedEntity(final String typeKeyName, final String entityId, final EventSourcePlugin plugin) {
        super(typeKeyName, entityId);
        this.plugin = plugin == null ? EventSourcePlugin.NoPlugin.INSTANCE : plugin;
    }

    public String journalPluginId() {
        return plugin.journalPluginId();
    }

    public String snapshotPluginId() {
        return plugin.snapshotPluginId();
    }

    public Optional<Config> journalPluginConfig() {
        return plugin.journalPluginConfig();
    }

    public Optional<Config> snapshotPluginConfig() {
        return plugin.snapshotPluginConfig();
    }
}
