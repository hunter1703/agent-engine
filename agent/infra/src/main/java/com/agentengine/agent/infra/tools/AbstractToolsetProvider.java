package com.agentengine.agent.infra.tools;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.agents.ReadonlyContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Shared base for toolset providers backed by a fixed list of tool factories. */
public abstract class AbstractToolsetProvider implements ToolsetProvider {

    private final ToolDescriptor descriptor;
    private final List<ToolDefinition> toolDefinitions;

    protected AbstractToolsetProvider(final ToolDescriptor descriptor, final List<ToolDefinition> toolDefinitions) {
        this.descriptor = descriptor;
        this.toolDefinitions = List.copyOf(toolDefinitions);
    }

    @Override
    public final ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public BaseToolset create(final Map<String, Object> toolConfig) {
        return new Toolset(toolDefinitions);
    }

    public record ToolDefinition(ToolDescriptor descriptor, Supplier<? extends BaseTool> factory) {}

    private record Toolset(List<ToolDefinition> toolDefinitions) implements BaseToolset {

        @Override
        public Flowable<BaseTool> getTools(final ReadonlyContext context) {
            return Flowable.fromIterable(toolDefinitions)
                    .map(ToolDefinition::factory)
                    .map(Supplier::get)
                    .cast(BaseTool.class);
        }

        @Override
        public void close() {
            // No resources to release.
        }
    }
}
