package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolsetProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolFactoryTest {

  @Test
  void shouldBuildOnlyStandaloneToolsFromConfiguredEntries() {
    final BaseTool runtimeTool = mock(BaseTool.class);
    final ToolProvider provider =
        new ToolProvider() {
          @Override
          public ToolDescriptor descriptor() {
            return new ToolDescriptor("shared_tool", "Shared tool", Map.of());
          }

          @Override
          public BaseTool create(final Map<String, Object> toolConfig) {
            return runtimeTool;
          }
        };
    final ToolFactory factory =
        new ToolFactory(
            new ToolCatalogImpl(
                instanceOf(List.of(provider)),
                instanceOf(List.<ToolsetProvider>of()),
                emptyDiscoveredToolProviders()));

    assertThat(factory.buildTools(List.of(new ToolsConfig("shared_tool", Map.of()))))
        .containsExactly(runtimeTool);
    assertThat(factory.buildToolsets(List.of(new ToolsConfig("shared_tool", Map.of())))).isEmpty();
  }

  @Test
  void shouldBuildOnlyToolsetsWithoutExpandingThemIntoIndividualTools() {
    final BaseTool runtimeTool = mock(BaseTool.class);
    final BaseToolset runtimeToolset =
        new BaseToolset() {
          @Override
          public Flowable<BaseTool> getTools(final com.google.adk.agents.ReadonlyContext context) {
            return Flowable.just(runtimeTool);
          }

          @Override
          public void close() {}
        };
    final ToolDescriptor memberDescriptor =
        new ToolDescriptor("member_tool", "Member tool", Map.of());
    final ToolsetProvider toolsetProvider =
        new ToolsetProvider() {
          @Override
          public ToolDescriptor descriptor() {
            return new ToolDescriptor("planning", "Planning toolset", Map.of());
          }

          @Override
          public List<ToolDescriptor> memberDescriptors() {
            return List.of(memberDescriptor);
          }

          @Override
          public BaseToolset create(final Map<String, Object> toolConfig) {
            return runtimeToolset;
          }
        };
    final ToolFactory factory =
        new ToolFactory(
            new ToolCatalogImpl(
                instanceOf(List.<ToolProvider>of()),
                instanceOf(List.of(toolsetProvider)),
                emptyDiscoveredToolProviders()));

    assertThat(factory.buildTools(List.of(new ToolsConfig("planning", Map.of())))).isEmpty();
    assertThat(factory.buildToolsets(List.of(new ToolsConfig("planning", Map.of()))))
        .containsExactly(runtimeToolset);
  }

  @SuppressWarnings("unchecked")
  private static <T> Instance<T> instanceOf(final List<T> values) {
    final Instance<T> instance = mock(Instance.class);
    when(instance.iterator()).thenReturn(values.iterator());
    return instance;
  }

  @SuppressWarnings("unchecked")
  private static DiscoveredToolProviders emptyDiscoveredToolProviders() {
    final Instance<Tool> tools = mock(Instance.class);
    when(tools.iterator()).thenReturn(List.<Tool>of().iterator());
    return new DiscoveredToolProviders(tools);
  }
}
