package com.agentengine.engine.builders.messagestore;

import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.MessageStoreConfig;
import com.agentengine.engine.api.builders.MessageStoreBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Singleton
public class MessageStoreProvider {

    private final Map<String, MessageStoreBuilder<?, ?>> typeVsBuilder;
    private final MessageStoreBuilder<?, ?> defaultBuilder;

    @Inject
    public MessageStoreProvider(final Instance<MessageStoreBuilder<?, ?>> allBuilders,
                                final InMemoryMessageStoreBuilder inMemoryMessageStoreBuilder) {
        this.typeVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), MessageStoreBuilder::type,
                Function.identity());
        this.defaultBuilder = inMemoryMessageStoreBuilder;
    }

    public <C extends MessageStoreConfig, S extends MessageStore> S get(final C config) {
        //noinspection unchecked
        final MessageStoreBuilder<C, S> builder = (MessageStoreBuilder<C, S>) typeVsBuilder.getOrDefault(config.getType(), defaultBuilder);
        return builder.build(config);
    }
}