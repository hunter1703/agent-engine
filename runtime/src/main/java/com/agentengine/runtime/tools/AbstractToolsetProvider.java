package com.agentengine.runtime.tools;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.agents.ReadonlyContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared base for toolset providers backed by a fixed list of tool factories.
 */
public abstract class AbstractToolsetProvider implements ToolsetProvider {

  private final ToolDescriptor descriptor;
  private final List<ToolDefinition> toolDefinitions;
  private final List<ToolDescriptor> memberDescriptors;

  protected AbstractToolsetProvider(final ToolDescriptor descriptor, final List<ToolDefinition> toolDefinitions) {
    this.descriptor = descriptor;
    this.toolDefinitions = List.copyOf(toolDefinitions);
    this.memberDescriptors = this.toolDefinitions.stream().map(ToolDefinition::descriptor).toList();
  }

  @Override
  public final ToolDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public final List<ToolDescriptor> memberDescriptors() {
    return memberDescriptors;
  }

  @Override
  public BaseToolset create(final Map<String, Object> toolConfig) {
    return new StaticToolset(toolDefinitions);
  }

  public record ToolDefinition(ToolDescriptor descriptor, Supplier<? extends BaseTool> factory) {
  }

  private static class StaticToolset implements BaseToolset {
    private final List<ToolDefinition> toolDefinitions;
    private StaticToolset(List<ToolDefinition> toolDefinitions) {
      this.toolDefinitions = toolDefinitions;
    }

    @Override
    public Flowable<BaseTool> getTools(final ReadonlyContext context) {
      return Flowable.fromIterable(toolDefinitions).map(ToolDefinition::factory).map(Supplier::get).cast(BaseTool.class);
    }

    @Override
    public void close() {
      // No resources to release.
    }
  }
}
