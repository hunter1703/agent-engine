package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.Tool;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class ContextManagerProvider {

    private final Map<String, ContextManagerBuilder<?, ?>> nameVsBuilder;
    private final ContextManagerBuilder<?, ?> defaultBuilder;

    @Inject
    public ContextManagerProvider(final Instance<ContextManagerBuilder<?, ?>> allBuilders,
                                  final LastNContextManagerBuilder lastNContextManagerBuilder) {
        this.nameVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), ContextManagerBuilder::type,
                Function.identity());
        this.defaultBuilder = lastNContextManagerBuilder;
    }

    public <C extends ContextManagerConfig, CM extends ContextManager> ContextManager get(final C config, final String protocolMessage, final List<Tool> tools) {
        //noinspection unchecked
        final ContextManagerBuilder<C, CM> builder = (ContextManagerBuilder<C, CM>) nameVsBuilder.getOrDefault(config.getType(), defaultBuilder);
        return builder.build(config, protocolMessage, tools);
    }
}
